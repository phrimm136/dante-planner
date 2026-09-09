import { createEntityListQueryKeys } from '@/lib/queryKeys'
import {
  useEntityListSpec,
  useEntityListI18n,
  type EntityListDataConfig,
} from '@/shared/entityCatalog'
import type { z } from 'zod'
import { ThemePackListSchema, ThemePackI18nSchema } from '../schemas/ThemePackSchemas'

export const themePackListQueryKeys = createEntityListQueryKeys('themePack')

export const THEME_PACK_LIST: EntityListDataConfig<
  z.infer<typeof ThemePackListSchema>,
  z.infer<typeof ThemePackI18nSchema>
> = {
  kind: 'themePack',
  specImport: () => import('@static/data/themePackList.json'),
  specSchema: ThemePackListSchema,
  i18nImport: (language) => import(`@static/i18n/${language}/themePack.json`),
  i18nSchema: ThemePackI18nSchema,
}

/** Theme pack spec map; suspends on initial load, not on language change */
export function useThemePackListSpec() {
  return useEntityListSpec(THEME_PACK_LIST)
}

/** Theme pack name map (ID -> {name, specialName?}); suspends while loading */
export function useThemePackListI18n() {
  return useEntityListI18n(THEME_PACK_LIST)
}
