import type { CSSProperties } from 'react'
import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"
import { formatDistanceToNowStrict, type Locale } from 'date-fns'
import { enUS, ja, ko, zhCN } from 'date-fns/locale'
import keywordMatch from '@static/i18n/EN/keywordMatch.json'
import { SINNERS } from './constants'
import i18n from './i18n'

/** Map app language codes to date-fns locales */
const dateFnsLocales: Record<string, Locale> = {
  EN: enUS,
  JP: ja,
  KR: ko,
  CN: zhCN,
}

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * Extracts sinner name from entity ID
 * ID format: T SS II (5 digits)
 *   T: Type (1=identity, 2=ego)
 *   SS: Sinner index (01-12)
 *   II: Entity index within sinner
 * Example: 10101 -> type 1, sinner 01 -> YiSang
 * Example: 20305 -> type 2, sinner 03 -> DonQuixote
 * @param id - Entity ID (identity or EGO)
 * @returns Sinner name (e.g., "YiSang", "Faust")
 */
export function getSinnerFromId(id: string): string {
  const sinnerIndex = parseInt(id.substring(1, 3), 10) - 1
  return SINNERS[sinnerIndex] || 'Unknown'
}

/**
 * Extract sinner code (1-12) from entity ID
 * @param id - Entity ID (identity or EGO)
 * @returns Sinner code as string (e.g., "1", "2", ... "12")
 */
export function getSinnerCodeFromId(id: string): string {
  return String(parseInt(id.substring(1, 3), 10))
}

/**
 * Maps a PascalCase keyword to its user-friendly display name
 * @param keyword - PascalCase keyword (e.g., "Combustion", "Laceration")
 * @returns Display name (e.g., "burn", "bleed") or original keyword if no mapping exists
 */
export function getKeywordDisplayName(keyword: string): string {
  return keywordMatch[keyword as keyof typeof keywordMatch] || keyword.toLowerCase()
}

/**
 * Calculates the byte length of a string using UTF-8 encoding
 * Used for validation against backend byte limits (e.g., note size, title size)
 * @param str - String to measure
 * @returns Byte length in UTF-8 encoding, or 0 if input is null/undefined
 * @example
 * calculateByteLength("Hello") // 5
 * calculateByteLength("안녕하세요") // 15 (3 bytes per Korean character)
 * calculateByteLength("") // 0
 * calculateByteLength(null) // 0
 */
export function calculateByteLength(str: string | null | undefined): number {
  if (!str) return 0
  return new TextEncoder().encode(str).length
}

/**
 * Gets the CSS style object for language-specific display fonts.
 * Used for title-style text (identity/EGO names, skill names, passive names, sanity section names).
 *
 * Font mapping:
 * - KR (Korean): KOTRA Bold
 * - EN (English): Mikodacs Regular + letter-spacing
 * - JP (Japanese): Corporate Logo Bold
 * - CN (Chinese): Chinese Font
 *
 * @param language - Optional language code. If not provided, uses current i18n language
 * @returns CSS style object with fontFamily and optional letterSpacing
 * @example
 * // In component with useTranslation
 * const { i18n } = useTranslation()
 * <span style={getDisplayFontForLanguage(i18n.language)}>Identity Name</span>
 */
export function getDisplayFontForLanguage(language?: string): React.CSSProperties {
  const lang = language ?? i18n.language

  switch (lang) {
    case 'KR':
      return { fontFamily: 'var(--font-kotra)' }
    case 'EN':
      return { fontFamily: 'var(--font-mikodacs)', letterSpacing: '0.05em' }
    case 'JP':
      return { fontFamily: 'var(--font-corporate)' }
    case 'CN':
      return { fontFamily: 'var(--font-chinese)' }
    default:
      return { fontFamily: 'var(--font-pretendard)' }
  }
}

/**
 * Gets the CSS font-family value for numeric display fonts.
 * Used for skill power values (base power, coin power) and other game-style numbers.
 *
 * @returns CSS font-family string for Excelsior Sans
 * @example
 * <span style={{ fontFamily: getDisplayFontForNumeric() }}>+5</span>
 */
export function getDisplayFontForNumeric(): string {
  return 'var(--font-excelsior)'
}

/**
 * Gets the CSS font-family value for UI label fonts.
 * Used for labels like "LV", "ATK", etc.
 *
 * @returns CSS font-family string for Bebas Neue
 * @example
 * <span style={{ fontFamily: getDisplayFontForLabel() }}>LV</span>
 */
export function getDisplayFontForLabel(): string {
  return 'var(--font-bebas)'
}

/**
 * Gets the appropriate line-height ratio for language-specific fonts.
 * Different fonts have different glyph heights - Korean/Chinese need more vertical space.
 *
 * Line height ratios:
 * - KR (Korean): 1.3 - KOTRA Bold has tall glyphs
 * - CN (Chinese): 1.1 - Chinese characters need vertical space
 * - JP (Japanese): 1.1 - Japanese fonts are moderately tall
 * - EN (English): 1.0 - Latin fonts are compact
 *
 * @param language - Optional language code. If not provided, uses current i18n language
 * @returns Line height ratio (number, e.g., 1.3)
 * @example
 * const lineHeight = getLineHeightForLanguage(i18n.language)
 * <div style={{ lineHeight }}>Text</div>
 */
export function getLineHeightForLanguage(language?: string): number {
  const lang = language ?? i18n.language

  switch (lang) {
    case 'KR':
      return 1.3
    case 'CN':
      return 1.1
    case 'JP':
      return 1.1
    case 'EN':
      return 1.0
    default:
      return 1.2
  }
}

/**
 * Gets the CSS font-faily value for Title fonts.
 * Used for Title Dante's Planner.
 *
 * @ returns CSS font-family string for Tagmarker
 * @example
 * <span style={{ fontFamily: getDisplayFontForTitle() }}>Dante's Planner</span>
 */
export function getDisplayFontForTitle(): CSSProperties {
  return { fontFamily: 'var(--font-tagmarker)', letterSpacing: '0.01em' }
}

/**
 * Validates if a string is a valid UUID v4 format
 * @param value - String to validate
 * @returns True if valid UUID v4 format
 */
export function isValidUUID(value: string): boolean {
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
  return uuidRegex.test(value)
}

/**
 * Formats a date to short relative time with i18n support.
 * Output format: "23m ago", "2h ago", "3d ago"
 *
 * @param date - Date to format (Date object or ISO string)
 * @param language - Optional language code. If not provided, uses current i18n language
 * @returns Short formatted relative time string
 * @example
 * formatShortRelativeTime(new Date()) // "0s ago"
 * formatShortRelativeTime("2024-01-01T00:00:00Z") // "3d ago"
 */
export function formatShortRelativeTime(date: Date | string, language?: string): string {
  const dateObj = typeof date === 'string' ? new Date(date) : date
  const lang = language ?? i18n.language
  const locale = dateFnsLocales[lang] ?? enUS

  const distance = formatDistanceToNowStrict(dateObj, { locale, addSuffix: true })

  // Shorten common patterns for all languages
  // English: "X seconds/minutes/hours/days/months/years ago"
  // Korean: "X초/분/시간/일/개월/년 전"
  // Japanese: "X秒/分/時間/日/か月/年前"
  // Chinese: "X 秒/分钟/小时/天/个月/年前"
  return distance
    // English
    .replace(/(\d+)\s*seconds?\s*ago/, '$1s ago')
    .replace(/(\d+)\s*minutes?\s*ago/, '$1m ago')
    .replace(/(\d+)\s*hours?\s*ago/, '$1h ago')
    .replace(/(\d+)\s*days?\s*ago/, '$1d ago')
    .replace(/(\d+)\s*months?\s*ago/, '$1mo ago')
    .replace(/(\d+)\s*years?\s*ago/, '$1y ago')
    // Korean
    .replace(/(\d+)초 전/, '$1초 전')
    .replace(/(\d+)분 전/, '$1분 전')
    .replace(/(\d+)시간 전/, '$1시간 전')
    .replace(/(\d+)일 전/, '$1일 전')
    .replace(/(\d+)개월 전/, '$1개월 전')
    .replace(/(\d+)년 전/, '$1년 전')
    // Japanese
    .replace(/(\d+)秒前/, '$1秒前')
    .replace(/(\d+)分前/, '$1分前')
    .replace(/(\d+)時間前/, '$1時間前')
    .replace(/(\d+)日前/, '$1日前')
    .replace(/(\d+)か月前/, '$1ヶ月前')
    .replace(/(\d+)年前/, '$1年前')
    // Chinese
    .replace(/(\d+)\s*秒钟?前/, '$1秒前')
    .replace(/(\d+)\s*分钟前/, '$1分前')
    .replace(/(\d+)\s*小时前/, '$1时前')
    .replace(/(\d+)\s*天前/, '$1天前')
    .replace(/(\d+)\s*个月前/, '$1月前')
    .replace(/(\d+)\s*年前/, '$1年前')
}
