import { I18N_LOCALE_MAP } from '@/lib/constants'

const RECENT_THRESHOLD_HOURS = 24

export const DATE_FORMATS = {
  LONG_DATE: { year: 'numeric', month: 'long', day: 'numeric' },
  SHORT_DATE_TIME: { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' },
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

export function formatCompactDate(dateString: string): string {
  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffHours = diffMs / (1000 * 60 * 60)

  if (diffHours < RECENT_THRESHOLD_HOURS) {
    return new Intl.DateTimeFormat(undefined, {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(date)
  }

  return new Intl.DateTimeFormat(undefined, {
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

export function formatFullDate(dateString: string): string {
  return new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(dateString))
}

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

const RELATIVE_UNITS: ReadonlyArray<readonly [Intl.RelativeTimeFormatUnit, number]> = [
  ['year', MS_PER_YEAR],
  ['month', MS_PER_MONTH],
  ['day', MS_PER_DAY],
  ['hour', MS_PER_HOUR],
  ['minute', MS_PER_MINUTE],
  ['second', MS_PER_SECOND],
]

function toRelativeTimeLocale(locale?: string): string | undefined {
  return locale ? (I18N_LOCALE_MAP[locale] ?? locale) : undefined
}

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

const relativeTimeFormatters = new Map<string | undefined, Intl.RelativeTimeFormat>()

function relativeTimeFormatter(locale: string | undefined): Intl.RelativeTimeFormat {
  let formatter = relativeTimeFormatters.get(locale)
  if (!formatter) {
    formatter = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })
    relativeTimeFormatters.set(locale, formatter)
  }
  return formatter
}

export function formatRelativeTime(dateString: string, locale?: string): string {
  const { value, unit } = relativeTimeParts(dateString)

  return relativeTimeFormatter(toRelativeTimeLocale(locale)).format(value, unit)
}

export function formatCompactRelativeTime(dateString: string, locale?: string): string {
  const { value, unit } = relativeTimeParts(dateString)

  return new Intl.RelativeTimeFormat(toRelativeTimeLocale(locale), {
    numeric: 'always',
    style: 'narrow',
  }).format(value, unit)
}

export function formatAnnouncementDate(dateStr: string, language: string): string {
  const [year = NaN, month = NaN, day = NaN] = dateStr.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  return date.toLocaleDateString(I18N_LOCALE_MAP[language] ?? 'en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}
