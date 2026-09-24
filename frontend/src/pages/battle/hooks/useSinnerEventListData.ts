import type { z } from 'zod'
import {
  useEntityListI18n,
  useEntityListSpec,
  type EntityListDataConfig,
} from '@/shared/entityCatalog'
import { SinnerEventNameListSchema, SinnerEventSpecListSchema } from '../schemas/SinnerEventSchemas'

export const SINNER_EVENT_LIST: EntityListDataConfig<
  z.infer<typeof SinnerEventSpecListSchema>,
  z.infer<typeof SinnerEventNameListSchema>
> = {
  kind: 'sinnerEvent',
  specImport: () => import('@static/data/sinnerEventSpecList.json'),
  specSchema: SinnerEventSpecListSchema,
  i18nImport: (language) => import(`@static/i18n/${language}/sinnerEventNameList.json`),
  i18nSchema: SinnerEventNameListSchema,
}

export function useSinnerEventListSpec() {
  return useEntityListSpec(SINNER_EVENT_LIST)
}

export function useSinnerEventListI18n() {
  return useEntityListI18n(SINNER_EVENT_LIST)
}
