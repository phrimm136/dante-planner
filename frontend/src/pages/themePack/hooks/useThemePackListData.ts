import { createEntityListQueryKeys } from '@/lib/queryKeys'
import { useEntityListSpec, type EntityListDataConfig } from '@/shared/entityCatalog'
import { THEME_PACK_LIST_I18N, type ThemePackI18n } from '@/shared/themePack'
import type { z } from 'zod'
import { ThemePackListSchema } from '../schemas/ThemePackSchemas'

export const themePackListQueryKeys = createEntityListQueryKeys('themePack')

export const THEME_PACK_LIST: EntityListDataConfig<
  z.infer<typeof ThemePackListSchema>,
  ThemePackI18n
> = {
  ...THEME_PACK_LIST_I18N,
  specImport: () => import('@static/data/themePackList.json'),
  specSchema: ThemePackListSchema,
}

export function useThemePackListSpec() {
  return useEntityListSpec(THEME_PACK_LIST)
}
