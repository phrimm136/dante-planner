import type { z } from 'zod'
import {
  useEntityListI18n,
  useEntityListSpec,
  type EntityListDataConfig,
} from '@/shared/entityCatalog'
import { AbEventNameListSchema, AbEventSpecListSchema } from '../schemas/AbEventSchemas'

export const AB_EVENT_LIST: EntityListDataConfig<
  z.infer<typeof AbEventSpecListSchema>,
  z.infer<typeof AbEventNameListSchema>
> = {
  kind: 'abEvent',
  specImport: () => import('@static/data/abEventSpecList.json'),
  specSchema: AbEventSpecListSchema,
  i18nImport: (language) => import(`@static/i18n/${language}/abEventNameList.json`),
  i18nSchema: AbEventNameListSchema,
}

export function useAbEventListSpec() {
  return useEntityListSpec(AB_EVENT_LIST)
}

export function useAbEventListI18n() {
  return useEntityListI18n(AB_EVENT_LIST)
}
