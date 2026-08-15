import i18n from '@/lib/i18n'

interface TitleSyncRouter {
  invalidate: () => Promise<unknown>
  state: { matches: ReadonlyArray<{ meta?: unknown }> }
}

/**
 * The title the deepest matched route published, or null when no match carries
 * one. Matches are ordered root-first, so the search runs from the end.
 */
export function titleFromMatches(matches: ReadonlyArray<{ meta?: unknown }>): string | null {
  for (const match of [...matches].reverse()) {
    const meta = match.meta
    if (!Array.isArray(meta) || meta.length === 0) continue

    const titleMeta = meta.find(
      (m): m is { title: string } =>
        typeof m === 'object' &&
        m !== null &&
        'title' in m &&
        typeof (m as { title: unknown }).title === 'string',
    )
    return titleMeta?.title ? titleMeta.title : null
  }
  return null
}

/**
 * Keep document.title in the current language.
 *
 * TanStack Router evaluates head() only during route matching, so a language
 * change leaves the previous language's title in place until something else
 * forces a match.
 */
export function syncTitleOnLanguageChange(router: TitleSyncRouter): void {
  i18n.on('languageChanged', async () => {
    // Invalidating re-runs the loaders so detail pages re-fetch localized data,
    // and re-evaluates head(), which republishes each match's meta.
    await router.invalidate()

    const title = titleFromMatches(router.state.matches)
    if (title !== null) {
      document.title = title
    }
  })
}
