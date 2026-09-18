import type { JSONContent } from '@tiptap/core'

/**
 * Storage envelope for a note section.
 *
 * `content` here is the envelope field — it holds a Tiptap doc, which itself
 * has its own `content` field (ProseMirror children). Same key name, two
 * different layers: `NoteContent.content` (this envelope) vs `doc.content`
 * (Tiptap's intrinsic children array). It is NOT double-wrapping; serialized
 * notes read `{"content":{"type":"doc","content":[...]}}` for this reason.
 * The envelope is the backend wire contract (PlannerContentValidator measures
 * this exact shape) and reserves room for future note-level metadata.
 */
export interface NoteContent {
  /** The Tiptap document (a bare JSONContent doc, not a nested NoteContent) */
  content: JSONContent
}

export interface NoteEditorProps {
  value: NoteContent
  onChange?: (value: NoteContent) => void
  placeholder?: string
  readOnly?: boolean
  className?: string
  maxBytes?: number
}

export interface LinkDialogProps {
  open: boolean
  onClose: () => void
  onConfirm: (url: string, text?: string) => void
  initialText?: string
}
