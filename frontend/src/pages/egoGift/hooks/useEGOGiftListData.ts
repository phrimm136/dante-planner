import { createEntityListQueryKeys } from '@/lib/queryKeys'
import {
  useEntityListData,
  useEntityListSpec,
  useEntityListI18n,
  useEntityListI18nDeferred,
  type EntityListDataConfig,
} from '@/shared/entityCatalog'
import type { z } from 'zod'
import { EGOGiftSpecListSchema, EGOGiftNameListSchema } from '../schemas/EGOGiftSchemas'

export const egoGiftListQueryKeys = createEntityListQueryKeys('egoGift')

const EGO_GIFT_LIST: EntityListDataConfig<
  z.infer<typeof EGOGiftSpecListSchema>,
  z.infer<typeof EGOGiftNameListSchema>
> = {
  kind: 'egoGift',
  specImport: () => import('@static/data/egoGiftSpecList.json'),
  specSchema: EGOGiftSpecListSchema,
  i18nImport: (language) => import(`@static/i18n/${language}/egoGiftNameList.json`),
  i18nSchema: EGOGiftNameListSchema,
  emptyI18n: {},
}

/** EGO Gift spec map; suspends on initial load, not on language change */
export function useEGOGiftListSpec() {
  return useEntityListSpec(EGO_GIFT_LIST)
}

/** EGO Gift name map; suspends while loading */
export function useEGOGiftListI18n() {
  return useEntityListI18n(EGO_GIFT_LIST)
}

/** EGO Gift name map for list filtering; empty while loading, never suspends */
export function useEGOGiftListI18nDeferred(): Record<string, string> {
  return useEntityListI18nDeferred(EGO_GIFT_LIST)
}

/** EGO Gift spec map and name map; suspends while loading */
export function useEGOGiftListData() {
  return useEntityListData(EGO_GIFT_LIST)
}
