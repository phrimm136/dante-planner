import { useEntityListI18n, type EntityListDataConfig } from '@/shared/entityCatalog'
import { ThemePackI18nSchema, type ThemePackI18n } from '../schemas/ThemePackI18nSchemas'

export const THEME_PACK_LIST_I18N: Pick<
  EntityListDataConfig<unknown, ThemePackI18n>,
  'kind' | 'i18nImport' | 'i18nSchema'
> = {
  kind: 'themePack',
  i18nImport: (language) => import(`@static/i18n/${language}/themePack.json`),
  i18nSchema: ThemePackI18nSchema,
}

export function useThemePackListI18n() {
  return useEntityListI18n(THEME_PACK_LIST_I18N)
}
