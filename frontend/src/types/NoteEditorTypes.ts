import type { JSONContent } from '@tiptap/core'

/**
 * Content structure for a note section
 * Wraps Tiptap's JSONContent
 */
export interface NoteContent {
  /** Rich text content as Tiptap JSONContent */
  content: JSONContent
}

/**
 * Props for the NoteEditor component
 * Follows controlled component pattern (value + onChange)
 */
export interface NoteEditorProps {
  /** Current note content value */
  value: NoteContent
  /** Callback when content changes */
  onChange: (value: NoteContent) => void
  /** Optional placeholder text for empty editor */
  placeholder?: string
  /** Whether the editor is in read-only mode (disables editing and changes placeholder) */
  readOnly?: boolean
  /** Optional class name for styling */
  className?: string
  /** Optional maximum byte limit for note content (displays byte counter if provided) */
  maxBytes?: number
}

// Note: ToolbarProps is defined locally in Toolbar.tsx since it includes
// the editor instance which is an implementation detail

/**
 * Props for the LinkDialog component
 */
export interface LinkDialogProps {
  /** Whether the dialog is open */
  open: boolean
  /** Callback to close the dialog */
  onClose: () => void
  /** Callback when link is confirmed */
  onConfirm: (url: string, text?: string) => void
  /** Initial display text (from selected text) */
  initialText?: string
}

// Re-export IImageUploadAdapter from canonical location
export type { IImageUploadAdapter } from '@/components/noteEditor/adapters/IImageUploadAdapter'

/**
 * Image metadata stored in editor content
 */
export interface NoteImage {
  /** URL of the image (blob URL or remote URL) */
  src: string
  /** Alt text for accessibility */
  alt?: string
  /** Optional title attribute */
  title?: string
}
