import { queryOptions, keepPreviousData } from '@tanstack/react-query'
import type { z } from 'zod'
import { validateData } from './validation'
import { STATIC_DATA_STALE_TIME } from './constants'

export interface StaticDataQueryOptionsOpts {
  /**
   * Keep the previous language's data visible while the next loads
   * (`placeholderData: keepPreviousData`) — prevents the i18n
   * language-switch flash.
   */
  keepPrevious?: boolean
}

/**
 * Builds TanStack `queryOptions` for a statically imported JSON module
 * validated against a Zod schema.
 *
 * The importer MUST be a thunk wrapping a literal or template
 * `import('@static/…')` expression — a fully variable `import(path)`
 * defeats Vite's static analysis and breaks code-splitting.
 *
 * @param queryKey - Cache key tuple from a query key factory
 * @param importer - Thunk returning the dynamic import promise
 * @param schema - Zod schema for the module's default export
 * @param context - Validation error label, e.g. `identity specList`
 *
 * @example
 * createStaticDataQueryOptions(
 *   identityListQueryKeys.spec(),
 *   () => import('@static/data/identitySpecList.json'),
 *   IdentitySpecListSchema,
 *   'identity specList',
 * )
 */
export function createStaticDataQueryOptions<T, TKey extends readonly unknown[]>(
  queryKey: TKey,
  importer: () => Promise<{ default: unknown }>,
  schema: z.ZodType<T>,
  context: string,
  opts?: StaticDataQueryOptionsOpts,
) {
  return queryOptions({
    queryKey,
    queryFn: async () => {
      const module = await importer()
      return validateData(module.default, schema, context)
    },
    staleTime: STATIC_DATA_STALE_TIME,
    placeholderData: opts?.keepPrevious ? keepPreviousData : undefined,
  })
}
