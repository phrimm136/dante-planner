import { createEntityListQueryKeys } from '@/lib/queryKeys'
import {
  useEntityListSpec,
  useEntityListI18n,
  type EntityListDataConfig,
} from '@/shared/entityCatalog'
import type { z } from 'zod'
import { EGOGiftSpecListSchema, EGOGiftNameListSchema } from '../schemas/EGOGiftSchemas'

export const egoGiftListQueryKeys = createEntityListQueryKeys('egoGift')

export const EGO_GIFT_LIST: EntityListDataConfig<
  z.infer<typeof EGOGiftSpecListSchema>,
  z.infer<typeof EGOGiftNameListSchema>
> = {
  kind: 'egoGift',
  specImport: () => import('@static/data/egoGiftSpecList.json'),
  specSchema: EGOGiftSpecListSchema,
  i18nImport: (language) => import(`@static/i18n/${language}/egoGiftNameList.json`),
  i18nSchema: EGOGiftNameListSchema,
}

export function useEGOGiftListSpec() {
  return useEntityListSpec(EGO_GIFT_LIST)
}

export function useEGOGiftListI18n() {
  return useEntityListI18n(EGO_GIFT_LIST)
}
