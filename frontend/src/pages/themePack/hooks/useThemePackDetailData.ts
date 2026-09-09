import { useSuspenseQuery } from '@tanstack/react-query'
import { createEntityDetailQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { ThemePackDetailSchema } from '../schemas/ThemePackSchemas'

export const themePackDetailQueryKeys = createEntityDetailQueryKeys('themePack')

function createThemePackDetailQueryOptions(id: string) {
  return createStaticDataQueryOptions(
    themePackDetailQueryKeys.detail(id),
    () => import(`@static/data/themePack/${id}.json`),
    ThemePackDetailSchema,
    `themePack / ${id}`,
  )
}

/**
 * Theme pack mechanics for one pack; suspends on initial load, not on language
 * change. Names live in the list i18n file — read them with useThemePackListI18n.
 */
export function useThemePackDetailSpec(id: string) {
  const { data } = useSuspenseQuery(createThemePackDetailQueryOptions(id))
  return data
}
