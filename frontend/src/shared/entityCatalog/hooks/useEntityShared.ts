import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { z } from 'zod'
import { createEntitySharedQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'

/**
 * Describes one entity's shared resources: a single language-scoped file for
 * the whole namespace, not per id.
 *
 * Importers stay in the owning slice so each `import('@static/…')` remains a
 * literal Vite can code-split; only the query wiring is shared.
 */
export interface EntitySharedDataConfig<TShared> {
  /** Entity namespace — drives the query key tuple and validation label */
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

/**
 * Loads the namespace's shared resources for the active language.
 * Suspends while loading — wrap in Suspense.
 */
export function useEntityShared<TShared>(cfg: EntitySharedDataConfig<TShared>): TShared {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(sharedOptions(cfg, i18n.language))
  return data
}
