/**
 * noteUtils.test.ts
 *
 * Unit tests for note content state utilities.
 */

import { describe, it, expect } from 'vitest'
import {
  isNoteEmpty,
  calculateNoteByteLength,
  measureDocBytes,
  largestPrefixWithinLimit,
} from '../noteUtils'
import { createEmptyNoteContent } from '../../schemas/NoteEditorSchemas'

describe('isNoteEmpty', () => {
  it('returns true for null', () => {
    expect(isNoteEmpty(null)).toBe(true)
  })

  it('returns true for undefined', () => {
    expect(isNoteEmpty(undefined)).toBe(true)
  })

  it('returns true for empty doc (no content children)', () => {
    expect(isNoteEmpty({ content: { type: 'doc', content: [] } })).toBe(true)
  })

  it('returns true for single empty paragraph (createEmptyNoteContent default)', () => {
    expect(isNoteEmpty({ content: { type: 'doc', content: [{ type: 'paragraph' }] } })).toBe(true)
  })

  it('returns true for whitespace-only text', () => {
    expect(
      isNoteEmpty({
        content: {
          type: 'doc',
          content: [
            {
              type: 'paragraph',
              content: [{ type: 'text', text: '   ' }],
            },
          ],
        },
      }),
    ).toBe(true)
  })

  it('returns false for real content', () => {
    expect(
      isNoteEmpty({
        content: {
          type: 'doc',
          content: [
            {
              type: 'paragraph',
              content: [{ type: 'text', text: 'hello' }],
            },
          ],
        },
      }),
    ).toBe(false)
  })

  it('returns true for missing content property', () => {
    expect(isNoteEmpty({} as never)).toBe(true)
  })

  it('returns true for doc with non-doc type', () => {
    expect(isNoteEmpty({ content: { type: 'text' } })).toBe(true)
  })

  it('returns false for structural leaf nodes (hardBreak)', () => {
    expect(
      isNoteEmpty({
        content: {
          type: 'doc',
          content: [{ type: 'hardBreak' }],
        },
      }),
    ).toBe(false)
  })
})

describe('calculateNoteByteLength', () => {
  it('counts the serialized note as exact UTF-8 bytes', () => {
    // {"content":{"type":"doc"}} — 26 ASCII characters = 26 bytes
    expect(calculateNoteByteLength({ content: { type: 'doc' } })).toBe(26)
  })

  it('measures bytes, not characters, for multibyte content', () => {
    const ascii = { content: { type: 'doc', text: 'aaa' } }
    const korean = { content: { type: 'doc', text: '가나다' } }

    // Same character count, but each Korean char is 3 UTF-8 bytes,
    // so the Korean note is 6 bytes larger (3 chars × 2 extra bytes).
    expect(calculateNoteByteLength(korean)).toBe(calculateNoteByteLength(ascii) + 6)
  })

  it('grows by exactly one byte per appended ASCII character', () => {
    const base = calculateNoteByteLength({ content: { type: 'doc', text: '' } })
    expect(calculateNoteByteLength({ content: { type: 'doc', text: 'abcde' } })).toBe(base + 5)
  })

  // Regression: the backend's PlannerContentValidator.validateNoteSize measures
  // objectMapper.writeValueAsString(entry.getValue()), where entry.getValue() is
  // the wrapped { "content": ... } object. A prior FE counter measured only the
  // inner doc, under-counting by the wrapper and letting users hit a backend
  // rejection while the UI showed them safely under the limit. This pins the
  // wrapper into the measurement so it cannot silently regress to inner-only.
  it('includes the { content } wrapper, matching the backend boundary', () => {
    const doc = { type: 'doc', content: [{ type: 'paragraph' }] }
    const inner = new TextEncoder().encode(JSON.stringify(doc)).length
    const wrapped = new TextEncoder().encode(JSON.stringify({ content: doc })).length

    expect(calculateNoteByteLength({ content: doc })).toBe(wrapped)
    // `{"content":` (11) + closing `}` (1) — the exact bytes the backend counts.
    expect(wrapped - inner).toBe(12)
  })
})

describe('measureDocBytes', () => {
  it('equals calculateNoteByteLength of the wrapped doc', () => {
    const doc = {
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: 'hi 가' }] }],
    }
    expect(measureDocBytes(doc)).toBe(calculateNoteByteLength({ content: doc }))
  })

  it('includes the +12 backend wrapper (does not double-wrap)', () => {
    const doc = { type: 'doc', content: [{ type: 'paragraph' }] }
    const inner = new TextEncoder().encode(JSON.stringify(doc)).length
    expect(measureDocBytes(doc) - inner).toBe(12)
  })
})

describe('largestPrefixWithinLimit', () => {
  // ASCII probe: 1 byte per character.
  const asciiMeasure = (s: string) => s.length

  it('returns full length when the whole string fits', () => {
    expect(largestPrefixWithinLimit('hello', asciiMeasure, 10)).toBe(5)
  })

  it('returns 0 when nothing fits', () => {
    expect(largestPrefixWithinLimit('hello', asciiMeasure, 0)).toBe(0)
  })

  it('returns the exact prefix at the inclusive boundary', () => {
    // limit 3 → "abc" (3) fits, "abcd" (4) does not.
    expect(largestPrefixWithinLimit('abcdef', asciiMeasure, 3)).toBe(3)
  })

  it('handles empty input', () => {
    expect(largestPrefixWithinLimit('', asciiMeasure, 100)).toBe(0)
  })

  it('respects a non-zero base offset in the measure', () => {
    // Each call costs 10 base + 1 per char; limit 13 → 3 chars.
    const offsetMeasure = (s: string) => 10 + s.length
    expect(largestPrefixWithinLimit('abcdef', offsetMeasure, 13)).toBe(3)
  })

  it('truncates multibyte text by bytes, not characters', () => {
    // Each '가' is 3 UTF-8 bytes; limit 6 → 2 characters.
    const byteMeasure = (s: string) => new TextEncoder().encode(s).length
    expect(largestPrefixWithinLimit('가나다라', byteMeasure, 6)).toBe(2)
  })

  it('never cuts between a surrogate pair (no lone surrogate)', () => {
    // '😀' = U+1F600 = 2 UTF-16 units (D83D DE00). Measure by code units so the
    // raw binary search would land at 3 ("ab" + high surrogate) — the guard
    // must step back to 2 so the prefix ends on a code-point boundary.
    const unitMeasure = (s: string) => s.length
    const best = largestPrefixWithinLimit('ab😀cd', unitMeasure, 3)

    expect(best).toBe(2)
    const prefix = 'ab😀cd'.slice(0, best)
    // No trailing lone high surrogate.
    expect((prefix.charCodeAt(prefix.length - 1) & 0xfc00) === 0xd800).toBe(false)
    expect(prefix).toBe('ab')
  })
})

// ============================================================================
// Double-wrap prevention
// ============================================================================
//
// The note storage shape is { content: <Tiptap doc> }. A "double wrap" is the
// bug class { content: { content: <doc> } } — caused by passing an already-
// wrapped NoteContent where a bare doc is expected. Tiptap's JSONContent has a
// `[key: string]: any` index signature, so the type system CANNOT reject this;
// these runtime invariants are the only regression net.

describe('double-wrap prevention', () => {
  const bareDoc = { type: 'doc', content: [{ type: 'paragraph' }] }

  /**
   * A correctly single-wrapped note's `.content` is a ProseMirror doc node, so
   * it has a string `type`. A double-wrapped `{content:{content:doc}}` has
   * `.content = {content:…}` with no `type` → the decisive discriminator.
   */
  const isSingleWrapped = (n: { content: { type?: unknown } }) =>
    typeof n.content?.type === 'string'

  it('createEmptyNoteContent() produces a single-wrapped note', () => {
    const note = createEmptyNoteContent()
    expect(isSingleWrapped(note)).toBe(true)
    expect((note.content as { type?: string }).type).toBe('doc')
    // The doc's own `content` is an array (children), not a nested envelope.
    expect(Array.isArray((note.content as { content?: unknown }).content)).toBe(true)
  })

  it('serialized single-wrap never contains the double-wrap signature', () => {
    const good = JSON.stringify({ content: bareDoc })
    const doubled = JSON.stringify({ content: { content: bareDoc } })

    expect(good).not.toContain('"content":{"content"')
    // Positive control: the signature DOES appear when actually double-wrapped.
    expect(doubled).toContain('"content":{"content"')
  })

  it('measureDocBytes does not double-wrap; double-wrapping is detectably larger', () => {
    const single = measureDocBytes(bareDoc)
    // Simulate the bug: a caller wraps an already-wrapped NoteContent.
    const doubled = calculateNoteByteLength({ content: { content: bareDoc } })

    expect(single).toBe(calculateNoteByteLength({ content: bareDoc }))
    // The extra envelope adds exactly another +12 — proves they are distinguishable.
    expect(doubled - single).toBe(12)
  })

  it('the real send-path shape ({ content: note.content }) stays single-wrapped', () => {
    // Mirrors createSaveablePlanner: serializableNotes[key] = { content: note.content }
    const note = createEmptyNoteContent()
    const wire = { content: note.content }

    expect(isSingleWrapped(wire)).toBe(true)
    expect(JSON.stringify(wire)).not.toContain('"content":{"content"')
  })
})
