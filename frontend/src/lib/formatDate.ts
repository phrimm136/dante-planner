/**
 * Date Formatting Utilities
 *
 * Provides timezone-aware date formatting for planner list display.
 * All formatting uses Intl.DateTimeFormat for proper localization.
 */

/**
 * Hours threshold for switching between time and date display
 */
const RECENT_THRESHOLD_HOURS = 24

/**
 * Format a date for display based on how old it is.
 *
 * - Less than 24 hours: Show HH:mm (e.g., "14:32")
 * - 24 hours or older: Show MM/DD (e.g., "12/30")
 *
 * All times displayed in user's local timezone.
 *
 * @param dateString - ISO 8601 date string from API
 * @returns Formatted date/time string
 *
 * @example
 * // If current time is 2024-12-31 15:00
 * formatPlannerDate("2024-12-31T10:30:00Z") // => "10:30" (same day)
 * formatPlannerDate("2024-12-25T10:30:00Z") // => "12/25" (older than 24h)
 */
export function formatPlannerDate(dateString: string): string {
  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffHours = diffMs / (1000 * 60 * 60)

  if (diffHours < RECENT_THRESHOLD_HOURS) {
    // Show time for recent items (within 24 hours)
    return new Intl.DateTimeFormat(undefined, {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(date)
  }

  // Show date for older items
  return new Intl.DateTimeFormat(undefined, {
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

/**
 * Format a date with both date and time for tooltips/detailed views.
 *
 * Shows full date and time in user's local timezone.
 *
 * @param dateString - ISO 8601 date string from API
 * @returns Full formatted date with time (e.g., "Dec 31, 2024, 14:32")
 *
 * @example
 * formatFullDate("2024-12-31T14:32:00Z")
 * // => "Dec 31, 2024, 14:32" (exact format depends on locale)
 */
export function formatFullDate(dateString: string): string {
  return new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(dateString))
}

/**
 * Format YYYYMMDD integer to localized date string.
 *
 * Used for entity release dates stored as integers (e.g., 20250109).
 *
 * @param dateInt - Date as YYYYMMDD integer (e.g., 20250109)
 * @param locale - BCP 47 locale string (e.g., 'en-US', 'ko-KR')
 * @returns Formatted date string (e.g., "Jan 9, 2025" for en-US)
 *
 * @example
 * formatEntityReleaseDate(20250109, 'en-US') // => "Jan 9, 2025"
 * formatEntityReleaseDate(20250109, 'ko-KR') // => "2025. 1. 9."
 */
export function formatEntityReleaseDate(dateInt: number, locale: string = 'en-US'): string {
  const dateStr = String(dateInt)
  const year = parseInt(dateStr.substring(0, 4), 10)
  const month = parseInt(dateStr.substring(4, 6), 10) - 1
  const day = parseInt(dateStr.substring(6, 8), 10)
  const date = new Date(year, month, day)

  return date.toLocaleDateString(locale, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

/**
 * Map app language codes to BCP 47 locale codes for Intl APIs
 */
const LOCALE_MAP: Record<string, string> = {
  EN: 'en-US',
  JP: 'ja',
  KR: 'ko',
  CN: 'zh-CN',
}

/**
 * Format a relative time string (e.g., "2 hours ago", "3 days ago").
 *
 * Useful for displaying how long ago something happened.
 *
 * @param dateString - ISO 8601 date string from API
 * @param locale - Optional app language code (EN/JP/KR/CN) or BCP 47 locale string
 * @returns Relative time string
 *
 * @example
 * formatRelativeTime("2024-12-31T10:00:00Z") // => "5 hours ago"
 * formatRelativeTime("2024-12-31T10:00:00Z", "KR") // => "5시간 전"
 */
export function formatRelativeTime(dateString: string, locale?: string): string {
  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffSeconds = Math.floor(diffMs / 1000)
  const diffMinutes = Math.floor(diffSeconds / 60)
  const diffHours = Math.floor(diffMinutes / 60)
  const diffDays = Math.floor(diffHours / 24)

  const bcp47Locale = locale ? (LOCALE_MAP[locale] ?? locale) : undefined
  const rtf = new Intl.RelativeTimeFormat(bcp47Locale, { numeric: 'auto' })

  if (diffDays > 0) {
    return rtf.format(-diffDays, 'day')
  }
  if (diffHours > 0) {
    return rtf.format(-diffHours, 'hour')
  }
  if (diffMinutes > 0) {
    return rtf.format(-diffMinutes, 'minute')
  }
  return rtf.format(-diffSeconds, 'second')
}
