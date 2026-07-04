import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createEntityDetailQueryKeys } from '@/lib/queryKeys'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import {
  AbEventDataSchema,
  AbEventI18nSchema,
  AbEventSharedSchema,
} from '../schemas/AbEventSchemas'

export const abEventDetailQueryKeys = {
  ...createEntityDetailQueryKeys('abEvent'),
  shared: (language: string) => ['abEvent', 'shared', language] as const,
}

function createAbEventDataQueryOptions(id: string) {
  return createStaticDataQueryOptions(
    abEventDetailQueryKeys.detail(id),
    () => import(`@static/data/abEvent/${id}.json`),
    AbEventDataSchema,
    `abEvent / ${id}`,
  )
}

function createAbEventI18nQueryOptions(id: string, language: string) {
  return createStaticDataQueryOptions(
    abEventDetailQueryKeys.i18n(id, language),
    () => import(`@static/i18n/${language}/abEvent/${id}.json`),
    AbEventI18nSchema,
    `abEvent i18n / ${id} / ${language}`,
  )
}

function createAbEventSharedQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    abEventDetailQueryKeys.shared(language),
    () => import(`@static/i18n/${language}/abEvent/_shared.json`),
    AbEventSharedSchema,
    `abEvent shared / ${language}`,
  )
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
  const { data: i18nData } = useSuspenseQuery(createAbEventI18nQueryOptions(id, i18n.language))
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
  const { data: i18nData } = useSuspenseQuery(createAbEventI18nQueryOptions(id, i18n.language))

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
  const { data: shared } = useSuspenseQuery(createAbEventSharedQueryOptions(i18n.language))
  return shared
}
