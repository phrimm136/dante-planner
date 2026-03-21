/**
 * Note Utilities
 *
 * Pure functions for checking note content state.
 */

import type { NoteContent } from '@/types/NoteEditorTypes'
import type { JSONContent } from '@tiptap/core'

/**
 * Checks whether a note is effectively empty.
 *
 * Handles all empty states:
 * - null/undefined input
 * - Missing or empty doc content
 * - Single empty paragraph (the default from createEmptyNoteContent)
 * - Whitespace-only text nodes
 *
 * @param note - NoteContent to check, or null/undefined
 * @returns true if the note has no meaningful content
 */
export function isNoteEmpty(note: NoteContent | null | undefined): boolean {
  if (!note?.content) return true

  const doc = note.content
  if (doc.type !== 'doc') return true
  if (!doc.content || doc.content.length === 0) return true

  return doc.content.every((node: JSONContent) => isNodeEmpty(node))
}

/**
 * Checks whether a single JSONContent node is empty.
 * A node is empty if it has no content children, or all children are whitespace-only text.
 */
function isNodeEmpty(node: JSONContent): boolean {
  if (!node.content || node.content.length === 0) {
    if (node.type === 'text') {
      return !node.text || node.text.trim().length === 0
    }
    return node.type === 'paragraph'
  }

  return node.content.every((child: JSONContent) => isNodeEmpty(child))
}
