import {
  useEntityDetailI18n,
  useEntityDetailSpec,
  type EntityDetailDataConfig,
} from '@/shared/entityCatalog'
import {
  AssistantDataSchema,
  BattleEntityI18nSchema,
  EnemyDataSchema,
  SinnerEventDataSchema,
  type AssistantData,
  type BattleEntityI18n,
  type EnemyData,
  type SinnerEventData,
} from '../schemas/BattleDetailSchemas'

export const ENEMY_DETAIL: EntityDetailDataConfig<EnemyData, BattleEntityI18n> = {
  kind: 'enemy',
  specImport: (id) => import(`@static/data/enemy/${id}.json`),
  specSchema: EnemyDataSchema,
  i18nImport: (id, language) => import(`@static/i18n/${language}/enemy/${id}.json`),
  i18nSchema: BattleEntityI18nSchema,
}

export const ASSISTANT_DETAIL: EntityDetailDataConfig<AssistantData, BattleEntityI18n> = {
  kind: 'assistant',
  specImport: (id) => import(`@static/data/assistant/${id}.json`),
  specSchema: AssistantDataSchema,
  i18nImport: (id, language) => import(`@static/i18n/${language}/assistant/${id}.json`),
  i18nSchema: BattleEntityI18nSchema,
}

export const SINNER_EVENT_DETAIL: EntityDetailDataConfig<SinnerEventData, BattleEntityI18n> = {
  kind: 'sinnerEvent',
  specImport: (id) => import(`@static/data/sinnerEvent/${id}.json`),
  specSchema: SinnerEventDataSchema,
  i18nImport: (id, language) => import(`@static/i18n/${language}/sinnerEvent/${id}.json`),
  i18nSchema: BattleEntityI18nSchema,
}

export function useBattleDetailSpec<TSpec>(
  config: EntityDetailDataConfig<TSpec, BattleEntityI18n>,
  id: string,
): TSpec {
  return useEntityDetailSpec(config, id)
}

export type BattleI18nConfig = EntityDetailDataConfig<unknown, BattleEntityI18n>

export function useBattleDetailI18n(config: BattleI18nConfig, id: string): BattleEntityI18n {
  return useEntityDetailI18n(config, id)
}
