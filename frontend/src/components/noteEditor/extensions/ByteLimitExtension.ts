import { Extension } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'

import { measureDocBytes } from '@/lib/noteUtils'

/**
 * Transaction meta key that exempts a transaction from the byte cap.
 * Set it on trusted programmatic content loads (e.g. external value-prop sync)
 * so the editor stays in sync with React state instead of silently rejecting.
 */
export const BYTE_LIMIT_BYPASS = 'byteLimitBypass'

/**
 * Byte Limit Extension for Tiptap
 *
 * Rejects any document-changing transaction whose resulting note would exceed
 * `limit` UTF-8 bytes, measured against the same { content } shape the backend
 * (PlannerContentValidator.validateNoteSize) and the Zod schema validate. This
 * makes the editor cap identical to the persistence gate: if the editor accepts
 * an edit, the save will not be rejected for size.
 *
 * Shrinking edits are always allowed so a note that is already over the limit
 * (e.g. legacy content loaded from storage) can still be trimmed back down.
 *
 * A `limit` of 0 disables enforcement (no plugin registered).
 */
export interface ByteLimitOptions {
  /** Maximum UTF-8 byte length of the serialized note; 0 disables the cap */
  limit: number
}

export const ByteLimitExtension = Extension.create<ByteLimitOptions>({
  name: 'byteLimit',

  addOptions() {
    return { limit: 0 }
  },

  addProseMirrorPlugins() {
    const { limit } = this.options
    if (!limit) return []

    return [
      new Plugin({
        key: new PluginKey('byteLimit'),
        filterTransaction(tr, state) {
          if (!tr.docChanged) return true

          // Trusted programmatic loads (server reload, import, legacy oversized
          // note from storage) carry this meta. They must not be gated as user
          // input — rejecting them silently desyncs the editor from React state.
          if (tr.getMeta(BYTE_LIMIT_BYPASS)) return true

          const nextSize = measureDocBytes(tr.doc.toJSON())
          if (nextSize <= limit) return true

          // Still over the cap: permit it only if it shrinks the note, so an
          // already-oversized note remains editable down to a valid size.
          const prevSize = measureDocBytes(state.doc.toJSON())
          return nextSize < prevSize
        },
      }),
    ]
  },
})

export default ByteLimitExtension
