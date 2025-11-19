import { useQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { IdentityData, IdentityI18n } from '@/types/IdentityTypes'
import type { EGOData, EGOI18n } from '@/types/EGOTypes'
import type { EGOGiftData, EGOGiftI18n } from '@/types/EGOGiftTypes'
import queryConfig from '@static/config/queryConfig.json'

// Entity type discriminator
export type EntityType = 'identity' | 'ego' | 'egoGift'

// Query key factory for hierarchical structure
export const entityQueryKeys = {
  all: (type: EntityType) => [type] as const,
  detail: (type: EntityType, id: string) => [type, id] as const,
  i18n: (type: EntityType, id: string, language: string) =>
    [type, id, 'i18n', language] as const,
}

// Entity configuration mapping types to paths and staleTime
const ENTITY_CONFIG = {
  identity: {
    dataPath: 'identity',
    i18nPath: 'identity',
    staleTime: queryConfig.staleTime.identity,
  },
  ego: {
    dataPath: 'EGO',
    i18nPath: 'EGO',
    staleTime: queryConfig.staleTime.ego,
  },
  egoGift: {
    dataPath: 'egoGift',
    i18nPath: 'egoGift',
    staleTime: queryConfig.staleTime.egoGift,
  },
} as const satisfies Record<EntityType, { dataPath: string; i18nPath: string; staleTime: number }>

// Generic data query options
function createDataQueryOptions(type: EntityType, id: string, enabled: boolean) {
  const config = ENTITY_CONFIG[type]
  return queryOptions({
    queryKey: entityQueryKeys.detail(type, id),
    queryFn: async () => {
      const module = await import(`@static/data/${config.dataPath}/${id}.json`)
      return module.default as IdentityData | EGOData | EGOGiftData
    },
    enabled,
    staleTime: config.staleTime,
  })
}

// Generic i18n query options
function createI18nQueryOptions(
  type: EntityType,
  id: string,
  language: string,
  enabled: boolean
) {
  const config = ENTITY_CONFIG[type]
  return queryOptions({
    queryKey: entityQueryKeys.i18n(type, id, language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/${config.i18nPath}/${id}.json`)
      return module.default as IdentityI18n | EGOI18n | EGOGiftI18n
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

  // First query: Load entity data (only execute when id exists)
  // Note: Pages validate id before calling this hook, so id! is safe here
  const dataQuery = useQuery(
    createDataQueryOptions(type, id!, !!id)
  )

  // Second query: Load i18n (dependent on data success)
  const i18nQuery = useQuery(
    createI18nQueryOptions(
      type,
      id!,
      i18n.language,
      dataQuery.isSuccess
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
