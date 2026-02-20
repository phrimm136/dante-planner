/**
 * Announcement types for the home page announcement section
 */

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
}
