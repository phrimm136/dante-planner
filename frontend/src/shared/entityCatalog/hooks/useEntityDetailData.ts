import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { z } from 'zod'
import { createEntityDetailQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'

/**
 * Describes one entity's detail data: the per-id spec file and its per-id,
 * per-language i18n file.
 *
 * Importers stay in the owning slice so each `import('@static/…')` keeps its
 * own glob and chunking; only the query wiring is shared.
 */
export interface EntityDetailDataConfig<TSpec, TI18n> {
  /** Entity namespace — drives the query key tuples and validation labels */
  kind: string
  specImport: (id: string) => Promise<{ default: unknown }>
  specSchema: z.ZodType<TSpec>
  i18nImport: (id: string, language: string) => Promise<{ default: unknown }>
  i18nSchema: z.ZodType<TI18n>
}

function specOptions<TSpec, TI18n>(cfg: EntityDetailDataConfig<TSpec, TI18n>, id: string) {
  return createStaticDataQueryOptions(
    createEntityDetailQueryKeys(cfg.kind).detail(id),
    () => cfg.specImport(id),
    cfg.specSchema,
    `${cfg.kind} / ${id}`,
  )
}

function i18nOptions<TSpec, TI18n>(
  cfg: EntityDetailDataConfig<TSpec, TI18n>,
  id: string,
  language: string,
) {
  return createStaticDataQueryOptions(
    createEntityDetailQueryKeys(cfg.kind).i18n(id, language),
    () => cfg.i18nImport(id, language),
    cfg.i18nSchema,
    `${cfg.kind} i18n / ${id} / ${language}`,
  )
}

/**
 * Loads the spec data only (no language dependency).
 * Suspends on initial load, but NOT on language change.
 */
export function useEntityDetailSpec<TSpec, TI18n>(
  cfg: EntityDetailDataConfig<TSpec, TI18n>,
  id: string,
): TSpec {
  const { data } = useSuspenseQuery(specOptions(cfg, id))
  return data
}

/**
 * Loads the i18n data only. Suspends while loading — wrap in Suspense.
 */
export function useEntityDetailI18n<TSpec, TI18n>(
  cfg: EntityDetailDataConfig<TSpec, TI18n>,
  id: string,
): TI18n {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(i18nOptions(cfg, id, i18n.language))
  return data
}

/**
 * Loads spec + i18n for one entity. Suspends while loading — wrap in Suspense.
 */
export function useEntityDetailData<TSpec, TI18n>(
  cfg: EntityDetailDataConfig<TSpec, TI18n>,
  id: string,
): { spec: TSpec; i18n: TI18n } {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(specOptions(cfg, id))
  const { data: i18nData } = useSuspenseQuery(i18nOptions(cfg, id, i18n.language))

  return {
    spec,
    i18n: i18nData,
  }
}
