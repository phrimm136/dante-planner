import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { z } from 'zod'
import { createEntityListQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'

export interface EntityListDataConfig<TSpec, TI18n> {
  kind: string
  specImport: () => Promise<{ default: unknown }>
  specSchema: z.ZodType<TSpec>
  i18nImport: (language: string) => Promise<{ default: unknown }>
  i18nSchema: z.ZodType<TI18n>
}

function specOptions<TSpec, TI18n>(cfg: EntityListDataConfig<TSpec, TI18n>) {
  return createStaticDataQueryOptions(
    createEntityListQueryKeys(cfg.kind).spec(),
    cfg.specImport,
    cfg.specSchema,
    `${cfg.kind} specList`,
  )
}

export function entityListI18nOptions<TSpec, TI18n>(
  cfg: EntityListDataConfig<TSpec, TI18n>,
  language: string,
) {
  return createStaticDataQueryOptions(
    createEntityListQueryKeys(cfg.kind).i18n(language),
    () => cfg.i18nImport(language),
    cfg.i18nSchema,
    `${cfg.kind} nameList / ${language}`,
  )
}

export function useEntityListSpec<TSpec, TI18n>(cfg: EntityListDataConfig<TSpec, TI18n>): TSpec {
  const { data } = useSuspenseQuery(specOptions(cfg))
  return data
}

export function useEntityListI18n<TSpec, TI18n>(cfg: EntityListDataConfig<TSpec, TI18n>): TI18n {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(entityListI18nOptions(cfg, i18n.language))
  return data
}
