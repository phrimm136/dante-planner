import { createEntityDetailQueryKeys } from '@/lib/queryKeys'
import {
  useEntityDetailSpec,
  useEntityDetailI18n,
  type EntityDetailDataConfig,
} from '@/shared/entityCatalog'
import type { z } from 'zod'
import { EGODataSchema, EGOI18nSchema } from '../schemas/EGOSchemas'

export const egoDetailQueryKeys = createEntityDetailQueryKeys('ego')

export const EGO_DETAIL: EntityDetailDataConfig<
  z.infer<typeof EGODataSchema>,
  z.infer<typeof EGOI18nSchema>
> = {
  kind: 'ego',
  specImport: (id) => import(`@static/data/ego/${id}.json`),
  specSchema: EGODataSchema,
  i18nImport: (id, language) => import(`@static/i18n/${language}/ego/${id}.json`),
  i18nSchema: EGOI18nSchema,
}

export function useEGODetailSpec(id: string) {
  return useEntityDetailSpec(EGO_DETAIL, id)
}

export function useEGODetailI18n(id: string) {
  return useEntityDetailI18n(EGO_DETAIL, id)
}
