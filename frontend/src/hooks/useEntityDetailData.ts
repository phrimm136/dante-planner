import { useQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { IdentityData, IdentityI18n } from '@/types/IdentityTypes'
import type { EGOData, EGOI18n } from '@/types/EGOTypes'
import type { EGOGiftData, EGOGiftI18n } from '@/types/EGOGiftTypes'
import queryConfig from '@static/config/queryConfig.json'
import { validateData, getDetailSchema, getI18nSchema } from '@/lib/validation'

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

// Generic data query options with runtime validation
function createDataQueryOptions(type: EntityType, id: string, enabled: boolean) {
  const config = ENTITY_CONFIG[type]
  return queryOptions({
    queryKey: entityQueryKeys.detail(type, id),
    queryFn: async () => {
      const module = await import(`@static/data/${config.dataPath}/${id}.json`)
      const schema = getDetailSchema(type)
      // Validate data before caching - throws descriptive error on validation failure
      return validateData<IdentityData | EGOData | EGOGiftData>(
        module.default,
        schema,
        { entityType: type, dataKind: 'detail', id }
      )
    },
    enabled,
    staleTime: config.staleTime,
  })
}

// Generic i18n query options with runtime validation
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
      const schema = getI18nSchema(type)
      // Validate i18n data before caching - throws descriptive error on validation failure
      return validateData<IdentityI18n | EGOI18n | EGOGiftI18n>(
        module.default,
        schema,
        { entityType: type, dataKind: 'i18n', id }
      )
    },
    enabled,
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads and validates entity detail data (spec + i18n)
 *
 * Runtime validation ensures JSON data matches TypeScript interfaces before caching.
 * Validation errors trigger error handling flow with descriptive toast notifications.
 * Successfully validated data is type-safe without requiring unsafe type assertions.
 *
 * @param type - Entity type (identity, ego, egoGift)
 * @param id - Entity ID (undefined disables queries)
 * @returns Validated entity data, i18n, loading and error states
 */
export function useEntityDetailData(type: EntityType, id: string | undefined) {
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
    data: dataQuery.data as (IdentityData | EGOData | EGOGiftData) | undefined,
    i18n: i18nQuery.data as (IdentityI18n | EGOI18n | EGOGiftI18n) | undefined,
    isPending: dataQuery.isPending || i18nQuery.isPending,
    isError: dataQuery.isError || i18nQuery.isError,
    error: dataQuery.error || i18nQuery.error,
  }
}
