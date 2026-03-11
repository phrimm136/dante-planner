import { z } from 'zod'

/**
 * Announcement Schemas
 *
 * Zod schemas for runtime validation of announcement spec and i18n data.
 *
 * @see hooks/useAnnouncementData.ts for usage
 */

// ============================================================================
// Spec Schemas
// ============================================================================

/**
 * Single announcement spec entry schema
 */
const dateString = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Expected YYYY-MM-DD format')

export const AnnouncementSpecSchema = z.object({
  id: z.string(),
  date: dateString,
  expiresAt: dateString.optional(),
}).strict()

/**
 * Announcement spec list schema (the full announcements.json array)
 */
export const AnnouncementSpecListSchema = z.array(AnnouncementSpecSchema)

// ============================================================================
// I18n Schemas
// ============================================================================

/**
 * Single i18n entry schema
 */
export const AnnouncementI18nEntrySchema = z.object({
  title: z.string(),
  body: z.string(),
}).strict()

/**
 * Full i18n file schema: Record<id, { title, body }>
 */
export const AnnouncementI18nSchema = z.record(z.string(), AnnouncementI18nEntrySchema)

// ============================================================================
// Inferred Types
// ============================================================================

export type AnnouncementSpec = z.infer<typeof AnnouncementSpecSchema>
export type AnnouncementI18nEntry = z.infer<typeof AnnouncementI18nEntrySchema>
export type AnnouncementI18n = z.infer<typeof AnnouncementI18nSchema>
