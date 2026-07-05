import { useSuspenseQuery, useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createEntityListQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { EGOSpecListSchema, EGONameListSchema } from '../schemas/EGOSchemas'

export const egoListQueryKeys = createEntityListQueryKeys('ego')

function createEGOSpecListQueryOptions() {
  return createStaticDataQueryOptions(
    egoListQueryKeys.spec(),
    () => import('@static/data/egoSpecList.json'),
    EGOSpecListSchema,
    'ego specList',
  )
}

function createEGONameListQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    egoListQueryKeys.i18n(language),
    () => import(`@static/i18n/${language}/egoNameList.json`),
    EGONameListSchema,
    `ego nameList / ${language}`,
    { keepPrevious: true },
  )
}

/**
 * Hook that loads EGO spec list only (no language dependency)
 * Suspends on initial load, but NOT on language change (key has no language)
 *
 * Use this in shell components that should stay stable during language change.
 *
 * @returns Validated EGO spec map
 */
export function useEGOListSpec() {
  const { data: spec } = useSuspenseQuery(createEGOSpecListQueryOptions())
  return spec
}

/**
 * Hook that loads and validates EGO name list only
 * Suspends while loading - wrap in Suspense boundary
 *
 * Use this in components wrapped in their own Suspense boundary
 * for granular loading states on language change.
 *
 * @returns Validated EGO name map
 */
export function useEGOListI18n() {
  const { i18n } = useTranslation()
  const { data: i18nData } = useSuspenseQuery(createEGONameListQueryOptions(i18n.language))
  return i18nData
}

// Empty name list constant for loading state
const EMPTY_NAME_LIST: Record<string, string> = {}

/**
 * Non-suspending version of useEGOListI18n for list filtering.
 * Returns empty object while loading - name search won't match anything.
 * Use this in list components to prevent suspension during language change.
 *
 * @returns EGO name map (id -> name), empty object while loading
 */
export function useEGOListI18nDeferred(): Record<string, string> {
  const { i18n } = useTranslation()
  const { data: i18nData } = useQuery(createEGONameListQueryOptions(i18n.language))
  return i18nData ?? EMPTY_NAME_LIST
}

/**
 * Hook that loads and validates EGO list data (spec list + name list)
 * Suspends while loading - wrap in Suspense boundary
 *
 * Returns spec map and name map separately for flexible consumption.
 *
 * @returns Validated EGO spec map and name map
 */
export function useEGOListData() {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(createEGOSpecListQueryOptions())
  const { data: i18nData } = useSuspenseQuery(createEGONameListQueryOptions(i18n.language))

  return {
    spec,
    i18n: i18nData,
  }
}
