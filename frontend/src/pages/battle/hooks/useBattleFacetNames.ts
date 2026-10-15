import { useEntityListI18n, type EntityListDataConfig } from '@/shared/entityCatalog'
import { BattleFacetNamesSchema, type BattleFacetNames } from '../schemas/BattleSpecSchemas'

export const BATTLE_FACET_NAMES: Pick<
  EntityListDataConfig<unknown, BattleFacetNames>,
  'kind' | 'i18nImport' | 'i18nSchema'
> = {
  kind: 'battleFacetNames',
  i18nImport: (language) => import(`@static/i18n/${language}/battleFacetNames.json`),
  i18nSchema: BattleFacetNamesSchema,
}

export function useBattleFacetNames() {
  return useEntityListI18n(BATTLE_FACET_NAMES)
}
