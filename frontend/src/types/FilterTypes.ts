import type { Season, Association } from '@/lib/constants'

/**
 * Seasons i18n data - maps season ID to localized name
 * Keys are string representations of Season type (e.g., "0", "1", "8000", "9101")
 */
export type SeasonsI18n = Record<`${Season}`, string>

/**
 * UnitKeywords i18n data - maps unit keyword code to localized name
 * Keys match Association type from constants
 */
export type UnitKeywordsI18n = Record<Association, string>
