import i18next from 'i18next'

/**
 * Format a username for display.
 *
 * Composes: {sinner}-{translatedKeyword}#{suffix}
 *
 * **i18n Dependency**: Requires `association` namespace (static/i18n/{lang}/association.json)
 * - `sinner`: Base username (e.g., "Faust")
 * - `{keyword}`: Association name (e.g., "W_CORP" → "WCorp")
 *
 * @param usernameKeyword - Association keyword (e.g., "W_CORP", "BLADE_LINEAGE")
 * @param usernameSuffix - Random alphanumeric suffix (e.g., "AB123")
 * @returns Formatted username string, or "Unknown" if fields are missing
 *
 * @example
 * // Given i18n/EN/association.json: { "sinner": "Faust", "W_CORP": "WCorp" }
 * formatUsername("W_CORP", "AB123") // => "Faust-WCorp#AB123"
 *
 * // If i18n key missing, uses keyword as-is
 * formatUsername("UNKNOWN_CORP", "AB123") // => "Faust-UNKNOWN_CORP#AB123"
 *
 * // If fields missing, returns fallback
 * formatUsername("", "") // => "Unknown"
 */
export function formatUsername(usernameKeyword: string, usernameSuffix: string): string {
  // Validate inputs - return fallback for missing data
  if (!usernameKeyword || !usernameSuffix) {
    if (import.meta.env.DEV) {
      console.warn('[formatUsername] Missing username fields:', { usernameKeyword, usernameSuffix })
    }
    return i18next.t('unknown', { ns: 'common', defaultValue: 'Unknown' })
  }

  const sinner = i18next.t('sinner', { ns: 'association' })
  const translatedKeyword = i18next.t(usernameKeyword, {
    ns: 'association',
    defaultValue: usernameKeyword,
  })

  // Warn in dev mode if translation key is missing
  if (import.meta.env.DEV && translatedKeyword === usernameKeyword) {
    console.warn(`[formatUsername] Missing i18n key: association.${usernameKeyword}`)
  }

  return `${sinner}-${translatedKeyword}#${usernameSuffix}`
}
