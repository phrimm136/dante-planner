import { z } from 'zod'
import type { NoteContent } from '@/types/NoteEditorTypes'

/**
 * Note Editor Schemas
 *
 * Zod schemas for runtime validation of NoteEditor data structures.
 * These schemas mirror the TypeScript interfaces in types/NoteEditorTypes.ts
 * and provide runtime validation for Tiptap JSONContent.
 *
 * JSONContent is a recursive structure that represents rich text content.
 * We use a permissive schema since Tiptap handles the detailed validation.
 */

/**
 * Schema for Tiptap marks (inline formatting like bold, italic, links)
 */
export const TiptapMarkSchema = z.object({
  type: z.string(),
  attrs: z.record(z.string(), z.unknown()).optional(),
}).strict()

/**
 * Schema for Tiptap JSONContent (recursive document structure)
 * This is a simplified schema that validates the basic structure.
 * Tiptap's internal validation handles the detailed node/mark constraints.
 */
export const JSONContentSchema: z.ZodType<unknown> = z.lazy(() =>
  z.object({
    type: z.string().optional(),
    attrs: z.record(z.string(), z.unknown()).optional(),
    content: z.array(JSONContentSchema).optional(),
    marks: z.array(TiptapMarkSchema).optional(),
    text: z.string().optional(),
  })
)

/**
 * Schema for NoteContent - wrapper around JSONContent
 */
export const NoteContentSchema = z.object({
  content: JSONContentSchema,
}).strict()

/**
 * Schema for an array of note sections
 */
export const NoteSectionsSchema = z.array(NoteContentSchema)

/**
 * Schema for NoteImage metadata
 */
export const NoteImageSchema = z.object({
  src: z.string(),
  alt: z.string().optional(),
  title: z.string().optional(),
}).strict()

/**
 * Empty note content factory - creates valid empty JSONContent
 */
export function createEmptyNoteContent(): NoteContent {
  return {
    content: {
      type: 'doc',
      content: [
        {
          type: 'paragraph',
        },
      ],
    },
  }
}
