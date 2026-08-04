import { createEntityDetailQueryKeys } from '@/lib/queryKeys'
import {
  useEntityDetailData,
  useEntityDetailSpec,
  useEntityDetailI18n,
  type EntityDetailDataConfig,
} from '@/shared/entityCatalog'
import type { z } from 'zod'
import { EGOGiftDataSchema, EGOGiftI18nSchema } from '../schemas/EGOGiftSchemas'

export const egoGiftDetailQueryKeys = createEntityDetailQueryKeys('egoGift')

const EGO_GIFT_DETAIL: EntityDetailDataConfig<
  z.infer<typeof EGOGiftDataSchema>,
  z.infer<typeof EGOGiftI18nSchema>
> = {
  kind: 'egoGift',
  specImport: (id) => import(`@static/data/egoGift/${id}.json`),
  specSchema: EGOGiftDataSchema,
  i18nImport: (id, language) => import(`@static/i18n/${language}/egoGift/${id}.json`),
  i18nSchema: EGOGiftI18nSchema,
}

/**
 * EGO Gift spec data; suspends on initial load, not on language change.
 *
 * @param id - EGO Gift ID (must be defined - validate in route first)
 */
export function useEGOGiftDetailSpec(id: string) {
  return useEntityDetailSpec(EGO_GIFT_DETAIL, id)
}

/**
 * EGO Gift i18n data; suspends while loading.
 *
 * @param id - EGO Gift ID (must be defined - validate in route first)
 */
export function useEGOGiftDetailI18n(id: string) {
  return useEntityDetailI18n(EGO_GIFT_DETAIL, id)
}

/**
 * EGO Gift spec + i18n; suspends while loading.
 *
 * @param id - EGO Gift ID (must be defined - validate in route first)
 */
export function useEGOGiftDetailData(id: string) {
  return useEntityDetailData(EGO_GIFT_DETAIL, id)
}
