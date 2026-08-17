import { createEntityListQueryKeys } from '@/lib/queryKeys'
import {
  useEntityListData,
  useEntityListSpec,
  useEntityListI18n,
  type EntityListDataConfig,
} from '@/shared/entityCatalog'
import type { z } from 'zod'
import { EGOSpecListSchema, EGONameListSchema } from '../schemas/EGOSchemas'

export const egoListQueryKeys = createEntityListQueryKeys('ego')

export const EGO_LIST: EntityListDataConfig<
  z.infer<typeof EGOSpecListSchema>,
  z.infer<typeof EGONameListSchema>
> = {
  kind: 'ego',
  specImport: () => import('@static/data/egoSpecList.json'),
  specSchema: EGOSpecListSchema,
  i18nImport: (language) => import(`@static/i18n/${language}/egoNameList.json`),
  i18nSchema: EGONameListSchema,
}

/** EGO spec map; suspends on initial load, not on language change */
export function useEGOListSpec() {
  return useEntityListSpec(EGO_LIST)
}

/** EGO name map; suspends while loading */
export function useEGOListI18n() {
  return useEntityListI18n(EGO_LIST)
}

/** EGO spec map and name map; suspends while loading */
export function useEGOListData() {
  return useEntityListData(EGO_LIST)
}
