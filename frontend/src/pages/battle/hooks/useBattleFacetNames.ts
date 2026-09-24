import { useEntityShared, type EntitySharedDataConfig } from '@/shared/entityCatalog'
import { BattleFacetNamesSchema, type BattleFacetNames } from '../schemas/BattleSpecSchemas'

export const BATTLE_FACET_NAMES: EntitySharedDataConfig<BattleFacetNames> = {
  kind: 'battleFacetNames',
  sharedImport: (language) => import(`@static/i18n/${language}/battleFacetNames.json`),
  sharedSchema: BattleFacetNamesSchema,
}

export function useBattleFacetNames() {
  return useEntityShared(BATTLE_FACET_NAMES)
}
