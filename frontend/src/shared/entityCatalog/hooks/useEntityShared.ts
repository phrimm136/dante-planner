import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { z } from 'zod'
import { createEntitySharedQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'

export interface EntitySharedDataConfig<TShared> {
  kind: string
  sharedImport: (language: string) => Promise<{ default: unknown }>
  sharedSchema: z.ZodType<TShared>
}

function sharedOptions<TShared>(cfg: EntitySharedDataConfig<TShared>, language: string) {
  return createStaticDataQueryOptions(
    createEntitySharedQueryKeys(cfg.kind).shared(language),
    () => cfg.sharedImport(language),
    cfg.sharedSchema,
    `${cfg.kind} shared / ${language}`,
  )
}

export function useEntityShared<TShared>(cfg: EntitySharedDataConfig<TShared>): TShared {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(sharedOptions(cfg, i18n.language))
  return data
}
