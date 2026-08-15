/**
 * Date Formatting Utilities
 *
 * Provides timezone-aware date formatting for planner list display.
 * All formatting uses Intl.DateTimeFormat for proper localization.
 */

import { I18N_LOCALE_MAP } from '@/lib/constants'

/**
 * Hours threshold for switching between time and date display
 */
const RECENT_THRESHOLD_HOURS = 24

/**
 * Intl option sets shared by the planner surfaces.
 */
export const DATE_FORMATS = {
  /** "December 31, 2024" */
  LONG_DATE: { year: 'numeric', month: 'long', day: 'numeric' },
  /** "Dec 31, 14:32" */
  SHORT_DATE_TIME: { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' },
  /** "Dec 31, 2024, 2:32 PM" */
  FULL_DATE_TIME_12H: {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  },
  /** "14:32:07" — matches the Date.toLocaleTimeString() default */
  TIME_ONLY: { hour: 'numeric', minute: 'numeric', second: 'numeric' },
} as const satisfies Record<string, Intl.DateTimeFormatOptions>

/**
 * Format an ISO 8601 timestamp, or return null when it cannot be formatted.
 *
 * Null is the "nothing to show" signal for every caller: missing input, a
 * string `Date` cannot parse, or an `Intl` rejection. Callers pick their own
 * placeholder rather than sharing one.
 *
 * @param dateString - ISO 8601 date string, possibly absent
 * @param locale - BCP 47 locale string; omit for the runtime default
 * @param options - Intl.DateTimeFormat options
 *
 * @example
 * formatPlannerDate("2024-12-31T14:32:00Z", "ko-KR", { dateStyle: 'long' })
 * formatPlannerDate("not a date") // => null
 */
export function formatPlannerDate(
  dateString: string | null | undefined,
  locale?: string,
  options?: Intl.DateTimeFormatOptions,
): string | null {
  if (!dateString) return null

  const date = new Date(dateString)
  if (Number.isNaN(date.getTime())) return null

  return new Intl.DateTimeFormat(locale, options).format(date)
}

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
 * formatCompactDate("2024-12-31T10:30:00Z") // => "10:30" (same day)
 * formatCompactDate("2024-12-25T10:30:00Z") // => "12/25" (older than 24h)
 */
export function formatCompactDate(dateString: string): string {
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

const MS_PER_SECOND = 1000
const MS_PER_MINUTE = 60 * MS_PER_SECOND
const MS_PER_HOUR = 60 * MS_PER_MINUTE
const MS_PER_DAY = 24 * MS_PER_HOUR
const MS_PER_MONTH = 30.436875 * MS_PER_DAY
const MS_PER_YEAR = 365.2425 * MS_PER_DAY

/** Largest first: the first unit the elapsed span fills whole is the one rendered. */
const RELATIVE_UNITS: ReadonlyArray<readonly [Intl.RelativeTimeFormatUnit, number]> = [
  ['year', MS_PER_YEAR],
  ['month', MS_PER_MONTH],
  ['day', MS_PER_DAY],
  ['hour', MS_PER_HOUR],
  ['minute', MS_PER_MINUTE],
  ['second', MS_PER_SECOND],
]

/**
 * App language code (EN/JP/KR/CN) to BCP 47, passing through anything already
 * a locale string. Undefined leaves the runtime default in charge.
 */
function toRelativeTimeLocale(locale?: string): string | undefined {
  return locale ? (I18N_LOCALE_MAP[locale] ?? locale) : undefined
}

/**
 * Signed unit count between now and `dateString`, negative for the past.
 */
function relativeTimeParts(dateString: string): {
  value: number
  unit: Intl.RelativeTimeFormatUnit
} {
  const elapsedMs = Date.now() - new Date(dateString).getTime()
  const magnitudeMs = Math.abs(elapsedMs)
  // A zero span keeps the -1: Intl reads -0 as past ("0s ago") and 0 as future ("in 0s").
  const direction = elapsedMs < 0 ? 1 : -1

  for (const [unit, unitMs] of RELATIVE_UNITS) {
    const value = Math.floor(magnitudeMs / unitMs)
    if (value > 0) return { value: direction * value, unit }
  }

  return { value: direction * Math.floor(magnitudeMs / MS_PER_SECOND), unit: 'second' }
}

/**
 * Format a relative time string (e.g., "2 hours ago", "yesterday").
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
  const { value, unit } = relativeTimeParts(dateString)

  return new Intl.RelativeTimeFormat(toRelativeTimeLocale(locale), { numeric: 'auto' }).format(
    value,
    unit,
  )
}

/**
 * Format a relative time string in its shortest localized form ("23m ago", "3d ago").
 *
 * @param dateString - ISO 8601 date string from API
 * @param locale - Optional app language code (EN/JP/KR/CN) or BCP 47 locale string
 * @returns Abbreviated relative time string
 *
 * @example
 * formatCompactRelativeTime("2024-12-31T10:00:00Z") // => "5h ago"
 * formatCompactRelativeTime("2024-12-31T10:00:00Z", "KR") // => "5시간 전"
 */
export function formatCompactRelativeTime(dateString: string, locale?: string): string {
  const { value, unit } = relativeTimeParts(dateString)

  return new Intl.RelativeTimeFormat(toRelativeTimeLocale(locale), {
    numeric: 'always',
    style: 'narrow',
  }).format(value, unit)
}

/**
 * Format "YYYY-MM-DD" date string for announcement display, respecting app language.
 *
 * Uses I18N_LOCALE_MAP from constants for correct BCP 47 locale strings.
 *
 * @param dateStr - Date string in "YYYY-MM-DD" format (e.g., "2026-02-19")
 * @param language - App language code (KR/EN/CN/JP)
 * @returns Formatted date string (e.g., "Feb 19, 2026" for EN, "2026. 2. 19." for KR)
 *
 * @example
 * formatAnnouncementDate("2026-02-19", "EN") // => "Feb 19, 2026"
 * formatAnnouncementDate("2026-02-19", "KR") // => "2026. 2. 19."
 */
export function formatAnnouncementDate(dateStr: string, language: string): string {
  const [year, month, day] = dateStr.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  return date.toLocaleDateString(I18N_LOCALE_MAP[language] ?? 'en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}
