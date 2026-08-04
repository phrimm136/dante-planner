import { useSuspenseQuery, useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { z } from 'zod'
import { createEntityListQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'

/**
 * Describes one entity's list data: the spec map (language-independent) and
 * the id → localized-name map.
 *
 * Importers stay in the owning slice so each `import('@static/…')` remains a
 * literal Vite can code-split; only the query wiring is shared.
 */
export interface EntityListDataConfig<TSpec, TI18n> {
  /** Entity namespace — drives the query key tuples and validation labels */
  kind: string
  specImport: () => Promise<{ default: unknown }>
  specSchema: z.ZodType<TSpec>
  i18nImport: (language: string) => Promise<{ default: unknown }>
  i18nSchema: z.ZodType<TI18n>
  /** Value the deferred i18n hook returns while the name list is loading */
  emptyI18n: TI18n
}

function specOptions<TSpec, TI18n>(cfg: EntityListDataConfig<TSpec, TI18n>) {
  return createStaticDataQueryOptions(
    createEntityListQueryKeys(cfg.kind).spec(),
    cfg.specImport,
    cfg.specSchema,
    `${cfg.kind} specList`,
  )
}

function i18nOptions<TSpec, TI18n>(cfg: EntityListDataConfig<TSpec, TI18n>, language: string) {
  return createStaticDataQueryOptions(
    createEntityListQueryKeys(cfg.kind).i18n(language),
    () => cfg.i18nImport(language),
    cfg.i18nSchema,
    `${cfg.kind} nameList / ${language}`,
    { keepPrevious: true },
  )
}

/**
 * Loads the spec list only (no language dependency).
 * Suspends on initial load, but NOT on language change.
 *
 * Use this in shell components that should stay stable during language change.
 */
export function useEntityListSpec<TSpec, TI18n>(cfg: EntityListDataConfig<TSpec, TI18n>): TSpec {
  const { data } = useSuspenseQuery(specOptions(cfg))
  return data
}

/**
 * Loads the name list only. Suspends while loading — wrap in Suspense.
 *
 * Use this in components wrapped in their own Suspense boundary for granular
 * loading states on language change.
 */
export function useEntityListI18n<TSpec, TI18n>(cfg: EntityListDataConfig<TSpec, TI18n>): TI18n {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(i18nOptions(cfg, i18n.language))
  return data
}

/**
 * Non-suspending name list for list filtering: returns `emptyI18n` while
 * loading, so a language switch never suspends the list.
 */
export function useEntityListI18nDeferred<TSpec, TI18n>(
  cfg: EntityListDataConfig<TSpec, TI18n>,
): TI18n {
  const { i18n } = useTranslation()
  const { data } = useQuery(i18nOptions(cfg, i18n.language))
  return data ?? cfg.emptyI18n
}

/**
 * Loads spec list + name list. Suspends while loading — wrap in Suspense.
 */
export function useEntityListData<TSpec, TI18n>(
  cfg: EntityListDataConfig<TSpec, TI18n>,
): { spec: TSpec; i18n: TI18n } {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(specOptions(cfg))
  const { data: i18nData } = useSuspenseQuery(i18nOptions(cfg, i18n.language))

  return {
    spec,
    i18n: i18nData,
  }
}
