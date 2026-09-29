import {
  useEntityListI18n,
  useEntityListSpec,
  type EntityListDataConfig,
} from '@/shared/entityCatalog'
import {
  BattleStageListSchema,
  BattleStageTextsSchema,
  type BattleStageList,
  type BattleStageTexts,
} from '../schemas/BattleDetailSchemas'

export const BATTLE_STAGES: EntityListDataConfig<BattleStageList, BattleStageTexts> = {
  kind: 'battleStage',
  specImport: () => import('@static/data/battleStageList.json'),
  specSchema: BattleStageListSchema,
  i18nImport: (language) => import(`@static/i18n/${language}/battleStageList.json`),
  i18nSchema: BattleStageTextsSchema,
}

export function useBattleStageListSpec() {
  return useEntityListSpec(BATTLE_STAGES)
}

export function useBattleStageListI18n() {
  return useEntityListI18n(BATTLE_STAGES)
}
