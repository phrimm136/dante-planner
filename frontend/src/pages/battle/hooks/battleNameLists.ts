import type { EntityListDataConfig } from '@/shared/entityCatalog'
import type { BattleFamily } from '../lib/battleVocabulary'
import type { BattleNameList } from '../schemas/BattleSpecSchemas'
import { ASSISTANT_LIST } from './useAssistantListData'
import { ENEMY_LIST } from './useEnemyListData'
import { SINNER_EVENT_LIST } from './useSinnerEventListData'

export type BattleNameListConfig = Pick<
  EntityListDataConfig<unknown, BattleNameList>,
  'kind' | 'i18nImport' | 'i18nSchema'
>

export const BATTLE_NAME_LISTS: Record<BattleFamily, BattleNameListConfig> = {
  enemy: ENEMY_LIST,
  assistant: ASSISTANT_LIST,
  sinnerEvent: SINNER_EVENT_LIST,
}
