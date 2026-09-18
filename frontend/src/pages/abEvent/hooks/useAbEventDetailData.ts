import { createEntityDetailQueryKeys, createEntitySharedQueryKeys } from '@/lib/queryKeys'
import {
  useEntityDetailSpec,
  useEntityDetailI18n,
  useEntityShared,
  type EntityDetailDataConfig,
  type EntitySharedDataConfig,
} from '@/shared/entityCatalog'
import type { z } from 'zod'
import {
  AbEventDataSchema,
  AbEventI18nSchema,
  AbEventSharedSchema,
} from '../schemas/AbEventSchemas'

export const abEventDetailQueryKeys = {
  ...createEntityDetailQueryKeys('abEvent'),
  ...createEntitySharedQueryKeys('abEvent'),
}

const AB_EVENT_DETAIL: EntityDetailDataConfig<
  z.infer<typeof AbEventDataSchema>,
  z.infer<typeof AbEventI18nSchema>
> = {
  kind: 'abEvent',
  specImport: (id) => import(`@static/data/abEvent/${id}.json`),
  specSchema: AbEventDataSchema,
  i18nImport: (id, language) => import(`@static/i18n/${language}/abEvent/${id}.json`),
  i18nSchema: AbEventI18nSchema,
}

export function useAbEventDetailSpec(id: string) {
  return useEntityDetailSpec(AB_EVENT_DETAIL, id)
}

export function useAbEventDetailI18n(id: string) {
  return useEntityDetailI18n(AB_EVENT_DETAIL, id)
}

const AB_EVENT_SHARED: EntitySharedDataConfig<z.infer<typeof AbEventSharedSchema>> = {
  kind: 'abEvent',
  sharedImport: (language) => import(`@static/i18n/${language}/abEvent/_shared.json`),
  sharedSchema: AbEventSharedSchema,
}

export function useAbEventShared() {
  return useEntityShared(AB_EVENT_SHARED)
}
