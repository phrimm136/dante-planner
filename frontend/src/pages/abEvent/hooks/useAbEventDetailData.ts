import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { AbEventDataSchema, AbEventI18nSchema, AbEventSharedSchema } from '../schemas/AbEventSchemas'

// Query key factory for AbEvent detail data
export const abEventDetailQueryKeys = {
  all: () => ['abEvent'] as const,
  detail: (id: string) => ['abEvent', id] as const,
  i18n: (id: string, language: string) => ['abEvent', id, 'i18n', language] as const,
  shared: (language: string) => ['abEvent', 'shared', language] as const,
}

// AbEvent mechanics query options with runtime validation
function createAbEventDataQueryOptions(id: string) {
  return queryOptions({
    queryKey: abEventDetailQueryKeys.detail(id),
    queryFn: async () => {
      const module = await import(`@static/data/abEvent/${id}.json`)
      const result = AbEventDataSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[abEvent / ${id}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

// AbEvent i18n query options with runtime validation
function createAbEventI18nQueryOptions(id: string, language: string) {
  return queryOptions({
    queryKey: abEventDetailQueryKeys.i18n(id, language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/abEvent/${id}.json`)
      const result = AbEventI18nSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[abEvent i18n / ${id} / ${language}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

// AbEvent shared resources query options
function createAbEventSharedQueryOptions(language: string) {
  return queryOptions({
    queryKey: abEventDetailQueryKeys.shared(language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/abEvent/_shared.json`)
      const result = AbEventSharedSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[abEvent shared / ${language}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads AbEvent spec data only (no language dependency)
 *
 * @param id - AbEvent ID
 * @returns Validated AbEvent mechanics data
 */
export function useAbEventDetailSpec(id: string) {
  const { data: spec } = useSuspenseQuery(createAbEventDataQueryOptions(id))
  return spec
}

/**
 * Hook that loads AbEvent i18n data only
 * Suspends while loading - wrap in Suspense boundary
 *
 * @param id - AbEvent ID
 * @returns Validated AbEvent i18n data
 */
export function useAbEventDetailI18n(id: string) {
  const { i18n } = useTranslation()
  const { data: i18nData } = useSuspenseQuery(
    createAbEventI18nQueryOptions(id, i18n.language)
  )
  return i18nData
}

/**
 * Hook that loads AbEvent detail data (spec + i18n)
 * Suspends while loading - wrap in Suspense boundary
 *
 * @param id - AbEvent ID
 * @returns Validated AbEvent mechanics and i18n data
 */
export function useAbEventDetailData(id: string) {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(createAbEventDataQueryOptions(id))
  const { data: i18nData } = useSuspenseQuery(
    createAbEventI18nQueryOptions(id, i18n.language)
  )

  return {
    spec,
    i18n: i18nData,
  }
}

/**
 * Hook that loads shared AbEvent resources (effect templates, targets, keywords)
 * Suspends while loading - wrap in Suspense boundary
 *
 * @returns Validated shared resources
 */
export function useAbEventShared() {
  const { i18n } = useTranslation()
  const { data: shared } = useSuspenseQuery(
    createAbEventSharedQueryOptions(i18n.language)
  )
  return shared
}
