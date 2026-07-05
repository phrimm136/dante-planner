/**
 * ByteLimitExtension.test.ts
 *
 * Tests the ProseMirror filterTransaction cap: over-limit edits are rejected,
 * in-limit edits pass, and shrinking edits are always allowed so an already
 * oversized note stays editable.
 */

import { describe, it, expect, afterEach } from 'vitest'
import { Editor } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'

import { calculateNoteByteLength } from '../../../lib/noteUtils'
import { MAX_NOTE_BYTES } from '@/lib/constants'
import { ByteLimitExtension, BYTE_LIMIT_BYPASS } from '../ByteLimitExtension'

function makeEditor(limit: number, content?: unknown) {
  return new Editor({
    element: document.createElement('div'),
    extensions: [StarterKit, ByteLimitExtension.configure({ limit })],
    content: content as never,
  })
}

function noteSize(editor: Editor): number {
  return calculateNoteByteLength({ content: editor.getJSON() })
}

let editor: Editor | undefined

afterEach(() => {
  editor?.destroy()
  editor = undefined
})

describe('ByteLimitExtension', () => {
  it('does not enforce a cap when limit is 0', () => {
    editor = makeEditor(0)
    editor.commands.insertContent('x'.repeat(5000))

    expect(editor.getText()).toHaveLength(5000)
    expect(noteSize(editor)).toBeGreaterThan(2048)
  })

  it('accepts an edit that stays within the limit', () => {
    editor = makeEditor(0)
    const emptyBytes = noteSize(editor)
    editor.destroy()

    editor = makeEditor(emptyBytes + 200)
    editor.commands.insertContent('short note')

    expect(editor.getText()).toContain('short note')
    expect(noteSize(editor)).toBeLessThanOrEqual(emptyBytes + 200)
  })

  it('rejects an edit that would exceed the limit', () => {
    editor = makeEditor(0)
    const emptyBytes = noteSize(editor)
    editor.destroy()

    editor = makeEditor(emptyBytes + 50)
    editor.commands.insertContent('a'.repeat(2000))

    // Transaction filtered out: oversized text never lands.
    expect(editor.getText()).not.toContain('a'.repeat(2000))
    expect(noteSize(editor)).toBeLessThanOrEqual(emptyBytes + 50)
  })

  it('allows shrinking edits even when the note is already over the limit', () => {
    const oversized = {
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: 'a'.repeat(5000) }] }],
    }
    // Tiny cap; initial content bypasses the filter (set at construction).
    editor = makeEditor(5, oversized)
    expect(noteSize(editor)).toBeGreaterThan(5)

    editor.chain().selectAll().deleteSelection().run()

    // A size-reducing transaction is permitted despite still-imperfect size.
    expect(editor.getText()).toBe('')
  })

  it('rejects a size-increasing edit when already over the limit', () => {
    const oversized = {
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: 'a'.repeat(3000) }] }],
    }
    editor = makeEditor(5, oversized)
    const before = editor.getText().length

    editor.commands.insertContentAt(editor.state.doc.content.size - 1, 'MORE')

    expect(editor.getText()).not.toContain('MORE')
    expect(editor.getText().length).toBe(before)
  })

  // Off-by-one at the REAL cap: backend rejects on `size > 2048`, so exactly
  // 2048 must be accepted and 2049 rejected. Far-from-boundary tests above
  // would not catch a `<=`→`<` regression in filterTransaction.
  it('accepts a note at exactly MAX_NOTE_BYTES and rejects one byte over', () => {
    // Measure structural size with a single ASCII char, then each further
    // ASCII char adds exactly 1 byte (no escaping) — so size is predictable.
    const probe = makeEditor(0)
    probe.commands.insertContent('a')
    const oneChar = noteSize(probe)
    probe.destroy()

    editor = makeEditor(MAX_NOTE_BYTES)
    // One transaction to 2047 (≤ cap → accepted by the filter).
    editor.commands.insertContent('a'.repeat(MAX_NOTE_BYTES - 1 - oneChar + 1))
    expect(noteSize(editor)).toBe(MAX_NOTE_BYTES - 1)

    // 2047 → 2048: exactly at the cap must be ACCEPTED (`<=`).
    editor.commands.insertContent('b')
    expect(noteSize(editor)).toBe(MAX_NOTE_BYTES)
    expect(editor.getText()).toContain('b')

    // 2048 → 2049: one byte over must be REJECTED, doc unchanged.
    editor.commands.insertContent('c')
    expect(noteSize(editor)).toBe(MAX_NOTE_BYTES)
    expect(editor.getText()).not.toContain('c')
  })

  it('allows an over-limit transaction that carries the bypass meta', () => {
    editor = makeEditor(0)
    const emptyBytes = noteSize(editor)
    editor.destroy()

    editor = makeEditor(emptyBytes + 50)
    // Same over-limit edit the plain test rejects — but tagged as a trusted
    // programmatic load, so filterTransaction must let it through.
    editor.chain().setMeta(BYTE_LIMIT_BYPASS, true).insertContent('a'.repeat(2000)).run()

    expect(editor.getText()).toContain('a'.repeat(2000))
    expect(noteSize(editor)).toBeGreaterThan(emptyBytes + 50)
  })

  it('bypass meta lets setContent load over-cap content (the NoteEditor prop-sync path)', () => {
    // Exact call shape NoteEditor uses to load an external/legacy oversized
    // note: chain().setMeta(bypass).setContent(doc).run(). Must populate the
    // editor instead of being rejected by the cap.
    editor = makeEditor(50)
    const oversized = {
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: 'X'.repeat(3000) }] }],
    }

    editor.chain().setMeta(BYTE_LIMIT_BYPASS, true).setContent(oversized).run()

    expect(editor.getText()).toContain('X'.repeat(3000))
    expect(noteSize(editor)).toBeGreaterThan(50)
  })
})
