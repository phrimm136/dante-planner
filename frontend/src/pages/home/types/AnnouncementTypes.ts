/**
 * Announcement types for the home page announcement section
 *
 * Boundary shapes are re-exported from their schemas (z.infer);
 * the merged view type below stays hand-written.
 */

export type {
  AnnouncementSpec,
  AnnouncementI18nEntry,
  AnnouncementI18n,
} from '../schemas/AnnouncementSchemas'

/**
 * Merged announcement (spec + i18n) — returned by useAnnouncementData
 */
export interface Announcement {
  id: string
  /** Raw "YYYY-MM-DD" date string */
  date: string
  /** Locale-formatted date string for display (e.g., "Feb 19, 2026" for EN) */
  formattedDate: string
  title: string
  body: string
  permanent: boolean
}
