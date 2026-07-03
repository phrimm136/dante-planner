import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createEntityDetailQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { IdentityDataSchema, IdentityI18nSchema } from '../schemas/IdentitySchemas'

export const identityDetailQueryKeys = createEntityDetailQueryKeys('identity')

function createIdentityDataQueryOptions(id: string) {
  return createStaticDataQueryOptions(
    identityDetailQueryKeys.detail(id),
    () => import(`@static/data/identity/${id}.json`),
    IdentityDataSchema,
    `identity / ${id}`,
  )
}

function createIdentityI18nQueryOptions(id: string, language: string) {
  return createStaticDataQueryOptions(
    identityDetailQueryKeys.i18n(id, language),
    () => import(`@static/i18n/${language}/identity/${id}.json`),
    IdentityI18nSchema,
    `identity i18n / ${id} / ${language}`,
  )
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
