import { queryOptions } from '@tanstack/react-query'
import type { z } from 'zod'
import { validateData } from './validation'
import { STATIC_DATA_STALE_TIME } from './constants'

/**
 * Builds TanStack `queryOptions` for a statically imported JSON module
 * validated against a Zod schema.
 *
 * The importer MUST be a thunk wrapping a literal or template
 * `import('@static/…')` expression — a fully variable `import(path)`
 * defeats Vite's static analysis and breaks code-splitting.
 */
export function createStaticDataQueryOptions<T, TKey extends readonly unknown[]>(
  queryKey: TKey,
  importer: () => Promise<{ default: unknown }>,
  schema: z.ZodType<T>,
  context: string,
) {
  return queryOptions({
    queryKey,
    queryFn: async () => {
      const module = await importer()
      return validateData(module.default, schema, context)
    },
    staleTime: STATIC_DATA_STALE_TIME,
  })
}
