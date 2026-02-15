import i18next from 'i18next'

/**
 * Format a username for display.
 *
 * Composes: {translatedEpithet}{sinner}#{suffix}
 *
 * **i18n Dependency**: Requires `epithet` namespace (static/i18n/{lang}/epithet.json)
 * - `sinner`: Base sinner name (e.g., "Faust")
 * - `{epithet}`: Epithet name (e.g., "NAIVE" → "Naive")
 *
 * @param usernameEpithet - Epithet keyword (e.g., "NAIVE", "BRILLIANT")
 * @param usernameSuffix - Random alphanumeric suffix (e.g., "AB123")
 * @param language - Current i18n language (pass `i18n.language` from `useTranslation()`)
 * @returns Formatted username string, or "Unknown" if fields are missing
 *
 * @example
 * // Given i18n/EN/epithet.json: { "sinner": "Faust", "NAIVE": "Naive" }
 * formatUsername("NAIVE", "AB123", "EN") // => "NaiveFaust#AB123"
 *
 * // If i18n key missing, uses epithet as-is
 * formatUsername("UNKNOWN", "AB123", "EN") // => "UNKNOWNFaust#AB123"
 *
 * // If fields missing, returns fallback
 * formatUsername("", "", "EN") // => "Unknown"
 */
export function formatUsername(usernameEpithet: string, usernameSuffix: string, language?: string): string {
  const lng = language || i18next.language

  // Validate inputs - return fallback for missing data
  if (!usernameEpithet || !usernameSuffix) {
    if (import.meta.env.DEV) {
      console.warn('[formatUsername] Missing username fields:', { usernameEpithet, usernameSuffix })
    }
    return i18next.t('unknown', { ns: 'common', lng, defaultValue: 'Unknown' })
  }

  const sinner = i18next.t('sinner', { ns: 'epithet', lng })
  const translatedEpithet = i18next.t(usernameEpithet, {
    ns: 'epithet',
    lng,
    defaultValue: usernameEpithet,
  })

  // Warn in dev mode if translation key is missing
  if (import.meta.env.DEV && translatedEpithet === usernameEpithet) {
    console.warn(`[formatUsername] Missing i18n key: epithet.${usernameEpithet}`)
  }

  return `${translatedEpithet}${sinner}#${usernameSuffix}`
}
