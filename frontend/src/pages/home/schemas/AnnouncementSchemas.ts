import { z } from 'zod'

const dateString = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Expected YYYY-MM-DD format')

export const AnnouncementSpecSchema = z
  .object({
    id: z.string(),
    date: dateString,
    expiresAt: dateString.optional(),
    permanent: z.boolean().optional(),
  })
  .strict()

export const AnnouncementSpecListSchema = z.array(AnnouncementSpecSchema)

export const AnnouncementI18nEntrySchema = z
  .object({
    title: z.string(),
    body: z.string(),
  })
  .strict()

export const AnnouncementI18nSchema = z.record(z.string(), AnnouncementI18nEntrySchema)

export type AnnouncementSpec = z.infer<typeof AnnouncementSpecSchema>
export type AnnouncementI18nEntry = z.infer<typeof AnnouncementI18nEntrySchema>
export type AnnouncementI18n = z.infer<typeof AnnouncementI18nSchema>
