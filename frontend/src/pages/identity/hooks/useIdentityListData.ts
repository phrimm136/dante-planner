import { createEntityListQueryKeys } from '@/lib/queryKeys'
import {
  useEntityListData,
  useEntityListSpec,
  useEntityListI18n,
  type EntityListDataConfig,
} from '@/shared/entityCatalog'
import type { z } from 'zod'
import { IdentitySpecListSchema, IdentityNameListSchema } from '../schemas/IdentitySchemas'

export const identityListQueryKeys = createEntityListQueryKeys('identity')

export const IDENTITY_LIST: EntityListDataConfig<
  z.infer<typeof IdentitySpecListSchema>,
  z.infer<typeof IdentityNameListSchema>
> = {
  kind: 'identity',
  specImport: () => import('@static/data/identitySpecList.json'),
  specSchema: IdentitySpecListSchema,
  i18nImport: (language) => import(`@static/i18n/${language}/identityNameList.json`),
  i18nSchema: IdentityNameListSchema,
}

/** Identity spec map; suspends on initial load, not on language change */
export function useIdentityListSpec() {
  return useEntityListSpec(IDENTITY_LIST)
}

/** Identity name map; suspends while loading */
export function useIdentityListI18n() {
  return useEntityListI18n(IDENTITY_LIST)
}

/** Identity spec map and name map; suspends while loading */
export function useIdentityListData() {
  return useEntityListData(IDENTITY_LIST)
}
