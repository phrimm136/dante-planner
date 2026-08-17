import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'
import keywordMatch from '@static/i18n/EN/keywordMatch.json'
import i18n from './i18n'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * Exhaustiveness guard for discriminated unions.
 *
 * Compiles only when every union member is already handled, so an added member
 * turns into a type error at each switch that forgot it.
 */
export function assertNever(value: never): never {
  throw new Error(`Unhandled union member: ${JSON.stringify(value)}`)
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
 * Validates if a string is a valid UUID v4 format
 * @param value - String to validate
 * @returns True if valid UUID v4 format
 */
export function isValidUUID(value: string): boolean {
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
  return uuidRegex.test(value)
}

/**
 * Clamps a number into the [0, 1] probability range
 * @param value - Number to clamp
 * @returns value bounded to [0, 1]
 * @example
 * clamp01(1.0000000000000002) // 1
 * clamp01(-1e-17) // 0
 */
export function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value))
}
