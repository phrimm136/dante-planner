/**
 * noteUtils.test.ts
 *
 * Unit tests for note content state utilities.
 */

import { describe, it, expect } from 'vitest'
import { isNoteEmpty } from '../noteUtils'

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
    expect(
      isNoteEmpty({ content: { type: 'doc', content: [{ type: 'paragraph' }] } })
    ).toBe(true)
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
      })
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
      })
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
      })
    ).toBe(false)
  })
})
