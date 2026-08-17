import { createEntityDetailQueryKeys } from '@/lib/queryKeys'
import {
  useEntityDetailData,
  useEntityDetailSpec,
  useEntityDetailI18n,
  type EntityDetailDataConfig,
} from '@/shared/entityCatalog'
import type { z } from 'zod'
import { EGODataSchema, EGOI18nSchema } from '../schemas/EGOSchemas'

export const egoDetailQueryKeys = createEntityDetailQueryKeys('ego')

const EGO_DETAIL: EntityDetailDataConfig<
  z.infer<typeof EGODataSchema>,
  z.infer<typeof EGOI18nSchema>
> = {
  kind: 'ego',
  specImport: (id) => import(`@static/data/ego/${id}.json`),
  specSchema: EGODataSchema,
  i18nImport: (id, language) => import(`@static/i18n/${language}/ego/${id}.json`),
  i18nSchema: EGOI18nSchema,
}

/**
 * EGO spec data; suspends on initial load, not on language change.
 *
 * @param id - EGO ID (must be defined - validate in route first)
 */
export function useEGODetailSpec(id: string) {
  return useEntityDetailSpec(EGO_DETAIL, id)
}

/**
 * EGO i18n data; suspends while loading.
 *
 * @param id - EGO ID (must be defined - validate in route first)
 */
export function useEGODetailI18n(id: string) {
  return useEntityDetailI18n(EGO_DETAIL, id)
}

/**
 * EGO spec + i18n; suspends while loading.
 *
 * @param id - EGO ID (must be defined - validate in route first)
 */
export function useEGODetailData(id: string) {
  return useEntityDetailData(EGO_DETAIL, id)
}
