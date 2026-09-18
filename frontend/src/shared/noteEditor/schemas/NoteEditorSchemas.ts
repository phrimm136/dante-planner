import { z } from 'zod'
import type { NoteContent } from '../types/NoteEditorTypes'

export const TiptapMarkSchema = z
  .object({
    type: z.string(),
    attrs: z.record(z.string(), z.unknown()).optional(),
  })
  .strict()

export const JSONContentSchema: z.ZodType<unknown> = z.lazy(() =>
  z.object({
    type: z.string().optional(),
    attrs: z.record(z.string(), z.unknown()).optional(),
    content: z.array(JSONContentSchema).optional(),
    marks: z.array(TiptapMarkSchema).optional(),
    text: z.string().optional(),
  }),
)

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
