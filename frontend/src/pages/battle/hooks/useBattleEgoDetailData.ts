import {
  useEntityDetailI18n,
  useEntityDetailSpec,
  type EntityDetailDataConfig,
} from '@/shared/entityCatalog'
import {
  BattleEgoDataSchema,
  BattleEgoI18nSchema,
  type BattleEgoData,
  type BattleEgoI18n,
} from '../schemas/BattleDetailSchemas'

const BATTLE_EGO_DETAIL: EntityDetailDataConfig<BattleEgoData, BattleEgoI18n> = {
  kind: 'battleEgo',
  specImport: (id) => import(`@static/data/ego/${id}.json`),
  specSchema: BattleEgoDataSchema,
  i18nImport: (id, language) => import(`@static/i18n/${language}/ego/${id}.json`),
  i18nSchema: BattleEgoI18nSchema,
}

export function useBattleEgoDetailSpec(id: string) {
  return useEntityDetailSpec(BATTLE_EGO_DETAIL, id)
}

export function useBattleEgoDetailI18n(id: string) {
  return useEntityDetailI18n(BATTLE_EGO_DETAIL, id)
}
