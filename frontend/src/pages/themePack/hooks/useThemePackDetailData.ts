import { useSuspenseQuery } from '@tanstack/react-query'
import { createEntityDetailQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { ThemePackDetailSchema } from '../schemas/ThemePackSchemas'

export const themePackDetailQueryKeys = createEntityDetailQueryKeys('themePack')

export function createThemePackDetailQueryOptions(id: string) {
  return createStaticDataQueryOptions(
    themePackDetailQueryKeys.detail(id),
    () => import(`@static/data/themePack/${id}.json`),
    ThemePackDetailSchema,
    `themePack / ${id}`,
  )
}

export function useThemePackDetailSpec(id: string) {
  const { data } = useSuspenseQuery(createThemePackDetailQueryOptions(id))
  return data
}
