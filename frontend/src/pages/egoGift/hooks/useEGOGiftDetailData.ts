import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createEntityDetailQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { EGOGiftDataSchema, EGOGiftI18nSchema } from '../schemas/EGOGiftSchemas'

export const egoGiftDetailQueryKeys = createEntityDetailQueryKeys('egoGift')

function createEGOGiftDataQueryOptions(id: string) {
  return createStaticDataQueryOptions(
    egoGiftDetailQueryKeys.detail(id),
    () => import(`@static/data/egoGift/${id}.json`),
    EGOGiftDataSchema,
    `egoGift / ${id}`,
  )
}

function createEGOGiftI18nQueryOptions(id: string, language: string) {
  return createStaticDataQueryOptions(
    egoGiftDetailQueryKeys.i18n(id, language),
    () => import(`@static/i18n/${language}/egoGift/${id}.json`),
    EGOGiftI18nSchema,
    `egoGift i18n / ${id} / ${language}`,
  )
}

/**
 * Hook that loads EGO Gift spec data only (no language dependency)
 * Suspends on initial load, but NOT on language change (key has no language)
 *
 * Use this in shell components that should stay stable during language change.
 *
 * @param id - EGO Gift ID (must be defined - validate in route first)
 * @returns Validated EGO Gift spec data
 */
export function useEGOGiftDetailSpec(id: string) {
  const { data: spec } = useSuspenseQuery(createEGOGiftDataQueryOptions(id))
  return spec
}

/**
 * Hook that loads EGO Gift i18n data only
 * Suspends while loading - wrap in Suspense boundary
 *
 * Use this in components wrapped in their own Suspense boundary
 * for granular loading states on language change.
 *
 * @param id - EGO Gift ID (must be defined - validate in route first)
 * @returns Validated EGO Gift i18n data
 */
export function useEGOGiftDetailI18n(id: string) {
  const { i18n } = useTranslation()
  const { data: i18nData } = useSuspenseQuery(createEGOGiftI18nQueryOptions(id, i18n.language))
  return i18nData
}

/**
 * Hook that loads and validates EGO Gift detail data (spec + i18n)
 * Suspends while loading - wrap in Suspense boundary
 *
 * Returns strongly typed EGOGiftData and EGOGiftI18n without requiring type assertions.
 *
 * @param id - EGO Gift ID (must be defined - validate in route first)
 * @returns Validated EGO Gift data and i18n
 */
export function useEGOGiftDetailData(id: string) {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(createEGOGiftDataQueryOptions(id))
  const { data: i18nData } = useSuspenseQuery(createEGOGiftI18nQueryOptions(id, i18n.language))

  return {
    spec,
    i18n: i18nData,
  }
}
