import { useQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import queryConfig from '@static/config/queryConfig.json'
import type { EntityType } from './useEntityDetailData'
import { validateData, getSpecListSchema, getNameListSchema } from '@/lib/validation'

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
    specPath: 'egoSpecList',
    i18nPath: 'egoNameList',
    staleTime: queryConfig.staleTime.ego,
  },
  egoGift: {
    specPath: 'egoGiftSpecList',
    i18nPath: 'egoGiftNameList',
    staleTime: queryConfig.staleTime.egoGift,
  },
} as const satisfies Record<EntityType, { specPath: string; i18nPath: string; staleTime: number }>

// Spec list query options with runtime validation
function createSpecListQueryOptions(type: EntityType) {
  const config = LIST_ENTITY_CONFIG[type]
  return queryOptions({
    queryKey: entityListQueryKeys.spec(type),
    queryFn: async () => {
      const module = await import(`@static/data/${config.specPath}.json`)
      const schema = getSpecListSchema(type)
      // Validate spec list before caching - throws descriptive error on validation failure
      return validateData<Record<string, any>>(
        module.default,
        schema,
        { entityType: type, dataKind: 'specList' }
      )
    },
    staleTime: config.staleTime,
  })
}

// I18n name list query options with runtime validation
function createI18nNameListQueryOptions(type: EntityType, language: string) {
  const config = LIST_ENTITY_CONFIG[type]
  return queryOptions({
    queryKey: entityListQueryKeys.i18n(type, language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/${config.i18nPath}.json`)
      const schema = getNameListSchema(type)
      // Validate name list before caching - throws descriptive error on validation failure
      return validateData<Record<string, string>>(
        module.default,
        schema,
        { entityType: type, dataKind: 'nameList' }
      )
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads and validates entity list data (spec list + name list)
 *
 * Runtime validation ensures JSON data matches TypeScript interfaces before caching.
 * Validation errors trigger error handling flow with descriptive toast notifications.
 * Successfully validated data is type-safe and merged with i18n names.
 *
 * @param type - Entity type (identity, ego, egoGift)
 * @returns Merged validated list data with i18n names, loading and error states
 */
export function useEntityListData<TListItem = any>(type: EntityType) {
  const { i18n } = useTranslation()

  // First query: Load spec list data
  const specQuery = useQuery(createSpecListQueryOptions(type))

  // Second query: Load i18n name list (dependent on spec success)
  const i18nQuery = useQuery(
    createI18nNameListQueryOptions(type, i18n.language)
  )

  // Merge spec and i18n data with type-specific field mapping
  const mergedData = specQuery.data && i18nQuery.data
    ? Object.entries(specQuery.data).map(([id, spec]) => {
        const base = {
          id,
          name: i18nQuery.data[id] || id, // Fallback to ID if no translation
          ...spec,
        }
        // EGO: map egoType to rank
        if (type === 'ego' && 'egoType' in spec) {
          return { ...base, rank: spec.egoType }
        }
        return base
      })
    : undefined

  return {
    data: mergedData as TListItem[] | undefined,
    isPending: specQuery.isPending || i18nQuery.isPending,
    isError: specQuery.isError || i18nQuery.isError,
    error: specQuery.error || i18nQuery.error,
  }
}
