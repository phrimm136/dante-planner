import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"
import keywordMatch from '@static/i18n/EN/keywordMatch.json'
import { SINNERS } from './constants'
import i18n from './i18n'

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
 * Gets the CSS font-family value for language-specific display fonts.
 * Used for title-style text (identity/EGO names, skill names, passive names, sanity section names).
 *
 * Font mapping:
 * - KR (Korean): KOTRA Bold
 * - EN (English): Mikodacs Regular
 * - JP (Japanese): Corporate Logo Bold
 * - CN (Chinese): Chinese Font
 *
 * @param language - Optional language code. If not provided, uses current i18n language
 * @returns CSS font-family string (CSS variable + fallbacks)
 * @example
 * // In component with useTranslation
 * const { i18n } = useTranslation()
 * <span style={{ fontFamily: getDisplayFontForLanguage(i18n.language) }}>Identity Name</span>
 */
export function getDisplayFontForLanguage(language?: string): string {
  const lang = language ?? i18n.language

  switch (lang) {
    case 'KR':
      return 'var(--font-kotra)'
    case 'EN':
      return 'var(--font-mikodacs)'
    case 'JP':
      return 'var(--font-corporate)'
    case 'CN':
      return 'var(--font-chinese)'
    default:
      return 'var(--font-pretendard)'
  }
}
