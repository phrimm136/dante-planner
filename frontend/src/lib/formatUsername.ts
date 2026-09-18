import i18next from 'i18next'

export function composeUsername(epithet: string, sinner: string, suffix: string): string {
  return `${epithet}${sinner}#${suffix}`
}

export function formatUsername(
  usernameEpithet: string | null | undefined,
  usernameSuffix: string | null | undefined,
  language?: string,
): string {
  const lng = language || i18next.language

  if (!usernameEpithet || !usernameSuffix) {
    if (import.meta.env.DEV) {
      console.warn('[formatUsername] Missing username fields:', { usernameEpithet, usernameSuffix })
    }
    return i18next.t('unknown', { ns: 'common', lng, defaultValue: 'Unknown' })
  }

  const translatedEpithet = i18next.t(usernameEpithet, {
    ns: 'epithet',
    lng,
    defaultValue: usernameEpithet,
  })

  if (import.meta.env.DEV && translatedEpithet === usernameEpithet) {
    console.warn(`[formatUsername] Missing i18n key: epithet.${usernameEpithet}`)
  }

  return composeUsername(
    translatedEpithet,
    i18next.t('sinner', { ns: 'epithet', lng }),
    usernameSuffix,
  )
}
