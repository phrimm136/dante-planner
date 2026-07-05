/**
 * Note Utilities
 *
 * Pure functions for checking note content state.
 */

import { calculateByteLength } from '@/lib/utils'
import type { NoteContent } from '../types/NoteEditorTypes'
import type { JSONContent } from '@tiptap/core'

/**
 * Computes the UTF-8 byte length of a serialized note.
 *
 * Mirrors the backend PlannerContentValidator.validateNoteSize boundary:
 * the backend serializes each section's note value object ({ content: ... })
 * and measures its UTF-8 byte length. Serializing the same { content } shape
 * here yields the identical byte count regardless of key ordering.
 *
 * @param note - the note content object ({ content: ... })
 * @returns UTF-8 byte length of the JSON-serialized note
 */
export function calculateNoteByteLength(note: { content: unknown }): number {
  return calculateByteLength(JSON.stringify(note))
}

/**
 * Measures a bare Tiptap doc as the backend would: wrapped in the storage
 * envelope ({ content: doc }), matching PlannerContentValidator.validateNoteSize.
 *
 * Prefer this over calling calculateNoteByteLength with an inline wrapper: the
 * `JSONContent` parameter type makes "bare doc in" a compile-time contract, so
 * passing an already-wrapped NoteContent (which would double-wrap to
 * { content: { content: ... } }) is a type error rather than a silent bug.
 *
 * @param doc - the bare Tiptap document (editor.getJSON() / node.toJSON())
 * @returns UTF-8 byte length the backend will measure for this note
 */
export function measureDocBytes(doc: JSONContent): number {
  return calculateNoteByteLength({ content: doc })
}

/**
 * Binary-searches the longest prefix of `text` whose `measure` stays within
 * `limit`. Used to truncate an over-limit paste to the largest slice that
 * still fits the byte cap.
 *
 * `measure` is monotonic in prefix length (more text never serializes smaller),
 * which is what makes the binary search valid.
 *
 * @param text - the full candidate string (e.g. pasted plain text)
 * @param measure - maps a prefix to its resulting size
 * @param limit - inclusive maximum the measured size may reach
 * @returns the largest prefix length that fits (0 if nothing fits)
 */
export function largestPrefixWithinLimit(
  text: string,
  measure: (candidate: string) => number,
  limit: number,
): number {
  let lo = 0
  let hi = text.length
  let best = 0
  while (lo <= hi) {
    const mid = (lo + hi) >> 1
    if (measure(text.slice(0, mid)) <= limit) {
      best = mid
      lo = mid + 1
    } else {
      hi = mid - 1
    }
  }
  // slice() cuts on UTF-16 units; if `best` lands between a surrogate pair the
  // prefix would end in a lone high surrogate (invalid text, JSON-escaped to
  // 6 bytes). Step back one unit so the prefix ends on a code-point boundary.
  if (best > 0 && (text.charCodeAt(best - 1) & 0xfc00) === 0xd800) {
    best--
  }
  return best
}

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
