import { useSuspenseQuery, useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createEntityListQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { IdentitySpecListSchema, IdentityNameListSchema } from '../schemas/IdentitySchemas'

export const identityListQueryKeys = createEntityListQueryKeys('identity')

function createIdentitySpecListQueryOptions() {
  return createStaticDataQueryOptions(
    identityListQueryKeys.spec(),
    () => import('@static/data/identitySpecList.json'),
    IdentitySpecListSchema,
    'identity specList',
  )
}

function createIdentityNameListQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    identityListQueryKeys.i18n(language),
    () => import(`@static/i18n/${language}/identityNameList.json`),
    IdentityNameListSchema,
    `identity nameList / ${language}`,
    { keepPrevious: true },
  )
}

/**
 * Hook that loads Identity spec list only (no language dependency)
 * Suspends on initial load, but NOT on language change (key has no language)
 *
 * Use this in shell components that should stay stable during language change.
 *
 * @returns Validated Identity spec map
 */
export function useIdentityListSpec() {
  const { data: spec } = useSuspenseQuery(createIdentitySpecListQueryOptions())
  return spec
}

/**
 * Hook that loads and validates Identity name list only
 * Suspends while loading - wrap in Suspense boundary
 *
 * Use this in components wrapped in their own Suspense boundary
 * for granular loading states on language change.
 *
 * @returns Validated Identity name map
 */
export function useIdentityListI18n() {
  const { i18n } = useTranslation()
  const { data: i18nData } = useSuspenseQuery(
    createIdentityNameListQueryOptions(i18n.language)
  )
  return i18nData
}

// Empty name list constant for loading state
const EMPTY_NAME_LIST: Record<string, string> = {}

/**
 * Non-suspending version of useIdentityListI18n for list filtering.
 * Returns empty object while loading - name search won't match anything.
 * Use this in list components to prevent suspension during language change.
 *
 * @returns Identity name map (id -> name), empty object while loading
 */
export function useIdentityListI18nDeferred(): Record<string, string> {
  const { i18n } = useTranslation()
  const { data: i18nData } = useQuery(
    createIdentityNameListQueryOptions(i18n.language)
  )
  return i18nData ?? EMPTY_NAME_LIST
}

/**
 * Hook that loads and validates identity list data (spec list + name list)
 * Suspends while loading - wrap in Suspense boundary
 *
 * Returns spec map and name map separately for flexible consumption.
 *
 * @returns Validated identity spec map and name map
 */
export function useIdentityListData() {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(createIdentitySpecListQueryOptions())
  const { data: i18nData } = useSuspenseQuery(
    createIdentityNameListQueryOptions(i18n.language)
  )

  return {
    spec,
    i18n: i18nData,
  }
}
