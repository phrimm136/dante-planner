import { useQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { IdentityData, IdentityI18n } from '@/types/IdentityTypes'
import type { EGOData, EGOI18n } from '@/types/EGOTypes'
import type { EGOGiftData, EGOGiftI18n } from '@/types/EGOGiftTypes'

// Entity type discriminator
export type EntityType = 'identity' | 'ego' | 'egogift'

// Query key factory for hierarchical structure
export const entityQueryKeys = {
  all: (type: EntityType) => [type] as const,
  detail: (type: EntityType, id: string) => [type, id] as const,
  i18n: (type: EntityType, id: string, language: string) =>
    [type, id, 'i18n', language] as const,
}

// Generic data query options
function createDataQueryOptions(type: EntityType, id: string) {
  return queryOptions({
    queryKey: entityQueryKeys.detail(type, id),
    queryFn: async () => {
      if (type === 'identity') {
        const module = await import(`@static/data/identity/${id}.json`)
        return module.default as IdentityData
      } else if (type === 'ego') {
        const module = await import(`@static/data/EGO/${id}.json`)
        return module.default as EGOData
      } else if (type === 'egogift') {
        const module = await import(`@static/data/egoGift/${id}.json`)
        return module.default as EGOGiftData
      }
      throw new Error(`Unknown entity type: ${type}`)
    },
    staleTime: type === 'egogift'
      ? 30 * 24 * 60 * 60 * 1000 // 30 days (data updates monthly)
      : 7 * 24 * 60 * 60 * 1000, // 7 days (data updates every 2 weeks)
  })
}

// Generic i18n query options
function createI18nQueryOptions(
  type: EntityType,
  id: string,
  language: string,
  enabled: boolean
) {
  return queryOptions({
    queryKey: entityQueryKeys.i18n(type, id, language),
    queryFn: async () => {
      if (type === 'identity') {
        const module = await import(`@static/i18n/${language}/identity/${id}.json`)
        return module.default as IdentityI18n
      } else if (type === 'ego') {
        const module = await import(`@static/i18n/${language}/EGO/${id}.json`)
        return module.default as EGOI18n
      } else if (type === 'egogift') {
        const module = await import(`@static/i18n/${language}/gift/${id}.json`)
        return module.default as EGOGiftI18n
      }
      throw new Error(`Unknown entity type: ${type}`)
    },
    enabled,
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

// Generic hook that combines data and i18n queries
export function useEntityDetailData<
  TData = IdentityData | EGOData | EGOGiftData,
  TI18n = IdentityI18n | EGOI18n | EGOGiftI18n
>(type: EntityType, id: string | undefined) {
  const { i18n } = useTranslation()

  // First query: Load entity data
  const dataQuery = useQuery(
    createDataQueryOptions(type, id || '')
  )

  // Second query: Load i18n (dependent on data success)
  const i18nQuery = useQuery(
    createI18nQueryOptions(
      type,
      id || '',
      i18n.language,
      dataQuery.isSuccess && !!id
    )
  )

  return {
    data: dataQuery.data as TData | undefined,
    i18n: i18nQuery.data as TI18n | undefined,
    isPending: dataQuery.isPending || i18nQuery.isPending,
    isError: dataQuery.isError || i18nQuery.isError,
    error: dataQuery.error || i18nQuery.error,
  }
}
