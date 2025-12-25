import { useQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { EGOGiftI18n } from '@/types/EGOGiftTypes'
import { EGOGiftI18nSchema } from '@/schemas'

// Query key factory for ego gift description
export const egoGiftDescriptionQueryKeys = {
  all: () => ['egoGift', 'description'] as const,
  detail: (id: string, language: string) =>
    [...egoGiftDescriptionQueryKeys.all(), id, language] as const,
}

// Description query options
function createDescriptionQueryOptions(id: string, language: string, enabled: boolean) {
  return queryOptions({
    queryKey: egoGiftDescriptionQueryKeys.detail(id, language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/egoGift/${id}.json`)
      const result = EGOGiftI18nSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[egoGift/${id}/${language}] Validation failed: ${result.error.message}`)
      }
      return result.data as EGOGiftI18n
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
    enabled,
  })
}

/**
 * Hook that lazy-loads ego gift description data on demand
 * @param id - Gift ID
 * @param enabled - Whether to fetch (set true on hover)
 * @returns Gift i18n data with name, descs, and obtain
 */
export function useEgoGiftDescription(id: string, enabled: boolean = false) {
  const { i18n } = useTranslation()
  const query = useQuery(createDescriptionQueryOptions(id, i18n.language, enabled))

  return {
    data: query.data,
    isPending: query.isPending,
    isError: query.isError,
    error: query.error,
    isFetched: query.isFetched,
  }
}
