import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { IdentityDataSchema, IdentityI18nSchema } from '@/schemas'

// Query key factory for identity detail data
export const identityDetailQueryKeys = {
  all: () => ['identity'] as const,
  detail: (id: string) => ['identity', id] as const,
  i18n: (id: string, language: string) => ['identity', id, 'i18n', language] as const,
}

// Identity data query options with runtime validation
function createIdentityDataQueryOptions(id: string) {
  return queryOptions({
    queryKey: identityDetailQueryKeys.detail(id),
    queryFn: async () => {
      const module = await import(`@static/data/identity/${id}.json`)
      const result = IdentityDataSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[identity / ${id}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

// Identity i18n query options with runtime validation
function createIdentityI18nQueryOptions(id: string, language: string) {
  return queryOptions({
    queryKey: identityDetailQueryKeys.i18n(id, language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/identity/${id}.json`)
      const result = IdentityI18nSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[identity i18n / ${id} / ${language}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads Identity spec data only (no language dependency)
 * Suspends on initial load, but NOT on language change (key has no language)
 *
 * Use this in shell components that should stay stable during language change.
 *
 * @param id - Identity ID (must be defined - validate in route first)
 * @returns Validated Identity spec data
 */
export function useIdentityDetailSpec(id: string) {
  const { data: spec } = useSuspenseQuery(createIdentityDataQueryOptions(id))
  return spec
}

/**
 * Hook that loads Identity i18n data only
 * Suspends while loading - wrap in Suspense boundary
 *
 * Use this in components wrapped in their own Suspense boundary
 * for granular loading states on language change.
 *
 * @param id - Identity ID (must be defined - validate in route first)
 * @returns Validated Identity i18n data
 */
export function useIdentityDetailI18n(id: string) {
  const { i18n } = useTranslation()
  const { data: i18nData } = useSuspenseQuery(
    createIdentityI18nQueryOptions(id, i18n.language)
  )
  return i18nData
}

/**
 * Hook that loads and validates identity detail data (spec + i18n)
 * Suspends while loading - wrap in Suspense boundary
 *
 * Returns strongly typed IdentityData and IdentityI18n without requiring type assertions.
 *
 * @param id - Identity ID (must be defined - validate in route first)
 * @returns Validated identity data and i18n
 */
export function useIdentityDetailData(id: string) {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(createIdentityDataQueryOptions(id))
  const { data: i18nData } = useSuspenseQuery(
    createIdentityI18nQueryOptions(id, i18n.language)
  )

  return {
    spec,
    i18n: i18nData,
  }
}
