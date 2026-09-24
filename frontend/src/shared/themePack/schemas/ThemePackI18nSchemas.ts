import { z } from 'zod'

export const ThemePackI18nEntrySchema = z
  .object({
    name: z.string(),
    specialName: z.string().optional(),
  })
  .strict()

export const ThemePackI18nSchema = z.record(z.string(), ThemePackI18nEntrySchema)

export type ThemePackI18n = z.infer<typeof ThemePackI18nSchema>
