import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { z } from 'zod'
import { createEntityDetailQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'

export interface EntityDetailDataConfig<TSpec, TI18n> {
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

export function useEntityDetailSpec<TSpec, TI18n>(
  cfg: EntityDetailDataConfig<TSpec, TI18n>,
  id: string,
): TSpec {
  const { data } = useSuspenseQuery(specOptions(cfg, id))
  return data
}

export function useEntityDetailI18n<TSpec, TI18n>(
  cfg: EntityDetailDataConfig<TSpec, TI18n>,
  id: string,
): TI18n {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(i18nOptions(cfg, id, i18n.language))
  return data
}
