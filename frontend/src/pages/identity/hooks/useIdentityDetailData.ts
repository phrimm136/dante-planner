import { createEntityDetailQueryKeys } from '@/lib/queryKeys'
import {
  useEntityDetailData,
  useEntityDetailSpec,
  useEntityDetailI18n,
  type EntityDetailDataConfig,
} from '@/shared/entityCatalog'
import type { z } from 'zod'
import { IdentityDataSchema, IdentityI18nSchema } from '../schemas/IdentitySchemas'

export const identityDetailQueryKeys = createEntityDetailQueryKeys('identity')

const IDENTITY_DETAIL: EntityDetailDataConfig<
  z.infer<typeof IdentityDataSchema>,
  z.infer<typeof IdentityI18nSchema>
> = {
  kind: 'identity',
  specImport: (id) => import(`@static/data/identity/${id}.json`),
  specSchema: IdentityDataSchema,
  i18nImport: (id, language) => import(`@static/i18n/${language}/identity/${id}.json`),
  i18nSchema: IdentityI18nSchema,
}

/**
 * Identity spec data; suspends on initial load, not on language change.
 *
 * @param id - Identity ID (must be defined - validate in route first)
 */
export function useIdentityDetailSpec(id: string) {
  return useEntityDetailSpec(IDENTITY_DETAIL, id)
}

/**
 * Identity i18n data; suspends while loading.
 *
 * @param id - Identity ID (must be defined - validate in route first)
 */
export function useIdentityDetailI18n(id: string) {
  return useEntityDetailI18n(IDENTITY_DETAIL, id)
}

/**
 * Identity spec + i18n; suspends while loading.
 *
 * @param id - Identity ID (must be defined - validate in route first)
 */
export function useIdentityDetailData(id: string) {
  return useEntityDetailData(IDENTITY_DETAIL, id)
}
