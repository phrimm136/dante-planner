import { useQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import queryConfig from '@static/config/queryConfig.json'
import type { EntityType } from './useEntityDetailData'

// Query key factory for list data
export const entityListQueryKeys = {
  all: (type: EntityType) => [type, 'list'] as const,
  spec: (type: EntityType) => [type, 'list', 'spec'] as const,
  i18n: (type: EntityType, language: string) => [type, 'list', 'i18n', language] as const,
}

// Entity configuration for list data
const LIST_ENTITY_CONFIG = {
  identity: {
    specPath: 'identitySpecList',
    i18nPath: 'identityNameList',
    staleTime: queryConfig.staleTime.identity,
  },
  ego: {
    specPath: 'EGOSpecList',
    i18nPath: 'EGONameList',
    staleTime: queryConfig.staleTime.ego,
  },
  egoGift: {
    specPath: 'EGOGiftSpecList',
    i18nPath: 'EGOGiftNameList',
    staleTime: queryConfig.staleTime.egoGift,
  },
} as const satisfies Record<EntityType, { specPath: string; i18nPath: string; staleTime: number }>

// Spec list query options with dynamic import
function createSpecListQueryOptions(type: EntityType) {
  const config = LIST_ENTITY_CONFIG[type]
  return queryOptions({
    queryKey: entityListQueryKeys.spec(type),
    queryFn: async () => {
      const module = await import(`@static/data/${config.specPath}.json`)
      return module.default as Record<string, any>
    },
    staleTime: config.staleTime,
  })
}

// I18n name list query options with dynamic import
function createI18nNameListQueryOptions(type: EntityType, language: string) {
  const config = LIST_ENTITY_CONFIG[type]
  return queryOptions({
    queryKey: entityListQueryKeys.i18n(type, language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/${config.i18nPath}.json`)
      return module.default as Record<string, string>
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

// Generic hook that loads and merges spec and i18n list data
export function useEntityListData<TListItem = any>(type: EntityType) {
  const { i18n } = useTranslation()

  // First query: Load spec list data
  const specQuery = useQuery(createSpecListQueryOptions(type))

  // Second query: Load i18n name list (dependent on spec success)
  const i18nQuery = useQuery(
    createI18nNameListQueryOptions(type, i18n.language)
  )

  // Merge spec and i18n data
  const mergedData = specQuery.data && i18nQuery.data
    ? Object.entries(specQuery.data).map(([id, spec]) => ({
        id,
        name: i18nQuery.data[id] || id, // Fallback to ID if no translation
        ...spec,
      }))
    : undefined

  return {
    data: mergedData as TListItem[] | undefined,
    isPending: specQuery.isPending || i18nQuery.isPending,
    isError: specQuery.isError || i18nQuery.isError,
    error: specQuery.error || i18nQuery.error,
  }
}
