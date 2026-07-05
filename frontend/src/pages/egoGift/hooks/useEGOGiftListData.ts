import { useSuspenseQuery, useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createEntityListQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { EGOGiftSpecListSchema, EGOGiftNameListSchema } from '../schemas/EGOGiftSchemas'

export const egoGiftListQueryKeys = createEntityListQueryKeys('egoGift')

function createEGOGiftSpecListQueryOptions() {
  return createStaticDataQueryOptions(
    egoGiftListQueryKeys.spec(),
    () => import('@static/data/egoGiftSpecList.json'),
    EGOGiftSpecListSchema,
    'egoGift specList',
  )
}

function createEGOGiftNameListQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    egoGiftListQueryKeys.i18n(language),
    () => import(`@static/i18n/${language}/egoGiftNameList.json`),
    EGOGiftNameListSchema,
    `egoGift nameList / ${language}`,
    { keepPrevious: true },
  )
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
  const { data: i18nData } = useSuspenseQuery(createEGOGiftNameListQueryOptions(i18n.language))
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
  const { data: i18nData } = useQuery(createEGOGiftNameListQueryOptions(i18n.language))
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
  const { data: i18nData } = useSuspenseQuery(createEGOGiftNameListQueryOptions(i18n.language))

  return {
    spec,
    i18n: i18nData,
  }
}
