import { useSuspenseQuery, useQuery, queryOptions, keepPreviousData } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { EGOGiftSpecListSchema, EGOGiftNameListSchema } from '@/schemas'

// Query key factory for EGO Gift list data
export const egoGiftListQueryKeys = {
  all: () => ['egoGift', 'list'] as const,
  spec: () => ['egoGift', 'list', 'spec'] as const,
  i18n: (language: string) => ['egoGift', 'list', 'i18n', language] as const,
}

// EGO Gift spec list query options with runtime validation
function createEGOGiftSpecListQueryOptions() {
  return queryOptions({
    queryKey: egoGiftListQueryKeys.spec(),
    queryFn: async () => {
      const response = await fetch('/data/egoGiftSpecList.json')
      if (!response.ok) throw new Error(`Failed to fetch egoGiftSpecList: ${response.status}`)
      const data: unknown = await response.json()
      const result = EGOGiftSpecListSchema.safeParse(data)
      if (!result.success) {
        throw new Error(`[egoGift specList] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

// EGO Gift name list query options with runtime validation
function createEGOGiftNameListQueryOptions(language: string) {
  return queryOptions({
    queryKey: egoGiftListQueryKeys.i18n(language),
    queryFn: async () => {
      const response = await fetch(`/i18n/${language}/egoGiftNameList.json`)
      if (!response.ok) throw new Error(`Failed to fetch egoGiftNameList: ${response.status}`)
      const data: unknown = await response.json()
      const result = EGOGiftNameListSchema.safeParse(data)
      if (!result.success) {
        throw new Error(`[egoGift nameList / ${language}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
    placeholderData: keepPreviousData,
  })
}

/**
 * Hook that loads EGO Gift spec list only (no language dependency)
 * Suspends on initial load, but NOT on language change (key has no language)
 *
 * Use this in shell components that should stay stable during language change.
 *
 * @returns Validated EGO Gift spec map
 */
export function useEGOGiftListSpec() {
  const { data: spec } = useSuspenseQuery(createEGOGiftSpecListQueryOptions())
  return spec
}

/**
 * Hook that loads and validates EGO Gift name list only
 * Suspends while loading - wrap in Suspense boundary
 *
 * Use this in components wrapped in their own Suspense boundary
 * for granular loading states on language change.
 *
 * @returns Validated EGO Gift name map
 */
export function useEGOGiftListI18n() {
  const { i18n } = useTranslation()
  const { data: i18nData } = useSuspenseQuery(
    createEGOGiftNameListQueryOptions(i18n.language)
  )
  return i18nData
}

// Empty name list constant for loading state
const EMPTY_NAME_LIST: Record<string, string> = {}

/**
 * Non-suspending version of useEGOGiftListI18n for list filtering.
 * Returns empty object while loading - name search won't match anything.
 * Use this in list components to prevent suspension during language change.
 *
 * @returns EGO Gift name map (id -> name), empty object while loading
 */
export function useEGOGiftListI18nDeferred(): Record<string, string> {
  const { i18n } = useTranslation()
  const { data: i18nData } = useQuery(
    createEGOGiftNameListQueryOptions(i18n.language)
  )
  return i18nData ?? EMPTY_NAME_LIST
}

/**
 * Hook that loads and validates EGO Gift list data (spec list + name list)
 * Suspends while loading - wrap in Suspense boundary
 *
 * Returns spec map and name map separately for flexible consumption.
 *
 * @returns Validated EGO Gift spec map and name map
 */
export function useEGOGiftListData() {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(createEGOGiftSpecListQueryOptions())
  const { data: i18nData } = useSuspenseQuery(
    createEGOGiftNameListQueryOptions(i18n.language)
  )

  return {
    spec,
    i18n: i18nData,
  }
}
