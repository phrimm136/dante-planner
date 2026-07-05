import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createEntityDetailQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { EGODataSchema, EGOI18nSchema } from '../schemas/EGOSchemas'

export const egoDetailQueryKeys = createEntityDetailQueryKeys('ego')

function createEGODataQueryOptions(id: string) {
  return createStaticDataQueryOptions(
    egoDetailQueryKeys.detail(id),
    () => import(`@static/data/ego/${id}.json`),
    EGODataSchema,
    `ego / ${id}`,
  )
}

function createEGOI18nQueryOptions(id: string, language: string) {
  return createStaticDataQueryOptions(
    egoDetailQueryKeys.i18n(id, language),
    () => import(`@static/i18n/${language}/ego/${id}.json`),
    EGOI18nSchema,
    `ego i18n / ${id} / ${language}`,
  )
}

/**
 * Hook that loads EGO spec data only (no language dependency)
 * Suspends on initial load, but NOT on language change (key has no language)
 *
 * Use this in shell components that should stay stable during language change.
 *
 * @param id - EGO ID (must be defined - validate in route first)
 * @returns Validated EGO spec data
 */
export function useEGODetailSpec(id: string) {
  const { data: spec } = useSuspenseQuery(createEGODataQueryOptions(id))
  return spec
}

/**
 * Hook that loads EGO i18n data only
 * Suspends while loading - wrap in Suspense boundary
 *
 * Use this in components wrapped in their own Suspense boundary
 * for granular loading states on language change.
 *
 * @param id - EGO ID (must be defined - validate in route first)
 * @returns Validated EGO i18n data
 */
export function useEGODetailI18n(id: string) {
  const { i18n } = useTranslation()
  const { data: i18nData } = useSuspenseQuery(createEGOI18nQueryOptions(id, i18n.language))
  return i18nData
}

/**
 * Hook that loads and validates EGO detail data (spec + i18n)
 * Suspends while loading - wrap in Suspense boundary
 *
 * Returns strongly typed EGOData and EGOI18n without requiring type assertions.
 *
 * @param id - EGO ID (must be defined - validate in route first)
 * @returns Validated EGO data and i18n
 */
export function useEGODetailData(id: string) {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(createEGODataQueryOptions(id))
  const { data: i18nData } = useSuspenseQuery(createEGOI18nQueryOptions(id, i18n.language))

  return {
    spec,
    i18n: i18nData,
  }
}
