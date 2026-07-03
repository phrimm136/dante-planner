import type { z } from 'zod'
import type { AtkType, DefType, Season, SkillAttributeType } from '@/shared/gameData'
import type {
  IdentityHpDataSchema,
  IdentityResistInfoSchema,
  IdentityMentalConditionInfoSchema,
  IdentitySkillDataEntrySchema,
  IdentitySkillDataTupleSchema,
  IdentitySkillEntrySchema,
  IdentitySkillsDataSchema,
  IdentityPassiveConditionSchema,
  IdentityPassiveListTupleSchema,
  IdentityPassivesDataSchema,
  IdentityDataSchema,
  IdentitySkillDescEntrySchema,
  IdentitySkillI18nSchema,
  IdentityPassiveI18nSchema,
  IdentityI18nSchema,
} from '../schemas/IdentitySchemas'

/**
 * Identity list item for list/grid views.
 *
 * Assembled in IdentityPage from already-validated spec + name list — not a
 * direct boundary shape, so it stays a plain TS type (deliberately uses plural
 * `attributeTypes`/`atkTypes`, unlike IdentitySpecListItemSchema's singular keys).
 * Name is optional - populated when i18n is loaded, but components should prefer
 * IdentityName component for granular Suspense boundaries.
 */
export interface IdentityListItem {
  id: string
  /** Optional - populated when i18n is loaded */
  name?: string
  rank: number
  updateDate: number
  unitKeywordList: string[]
  skillKeywordList: string[]
  battleKeywordList: string[]
  /** Skill attribute types (affinities) - e.g. ['AZURE', 'VIOLET', 'AMBER'] */
  attributeTypes: SkillAttributeType[]
  /** Attack types per skill - e.g. ['SLASH', 'PENETRATE', 'HIT'] */
  atkTypes: AtkType[]
  /** Defense types - e.g. ['GUARD'] or ['EVADE', 'COUNTER'] */
  defenseTypes: DefType[]
  /** Season identifier (0=Standard, 1-6=Seasons, 8000=Collab, 9101+=Walpurgis) */
  season: Season
}

/**
 * @deprecated Use IdentityListItem instead. Kept for backwards compatibility.
 */
export interface Identity {
  id: string
  /** Optional - only populated when i18n is loaded. Display uses IdentityName component. */
  name?: string
  rank: number
  updateDate: number
  unitKeywordList: string[]
  skillKeywordList: string[]
  /** Skill attribute types (affinities) - e.g. ['AZURE', 'VIOLET', 'AMBER'] */
  attributeTypes: SkillAttributeType[]
  /** Attack types per skill - e.g. ['SLASH', 'PENETRATE', 'HIT'] */
  atkTypes: AtkType[]
  /** Defense types - e.g. ['GUARD'] or ['EVADE', 'COUNTER'] */
  defenseTypes: DefType[]
  /** Season identifier (0=Standard, 1-6=Seasons, 8000=Collab, 9101+=Walpurgis) */
  season: Season
}

export type { Uptie } from '@/shared/gameData'

export type IdentityHpData = z.infer<typeof IdentityHpDataSchema>
export type IdentityResistInfo = z.infer<typeof IdentityResistInfoSchema>
export type IdentityMentalConditionInfo = z.infer<typeof IdentityMentalConditionInfoSchema>
export type IdentitySkillDataEntry = z.infer<typeof IdentitySkillDataEntrySchema>
export type IdentitySkillDataTuple = z.infer<typeof IdentitySkillDataTupleSchema>
export type IdentitySkillEntry = z.infer<typeof IdentitySkillEntrySchema>
export type IdentitySkillsData = z.infer<typeof IdentitySkillsDataSchema>
export type IdentityPassiveCondition = z.infer<typeof IdentityPassiveConditionSchema>
export type IdentityPassiveListTuple = z.infer<typeof IdentityPassiveListTupleSchema>
export type IdentityPassivesData = z.infer<typeof IdentityPassivesDataSchema>
export type IdentityData = z.infer<typeof IdentityDataSchema>
export type IdentitySkillDescEntry = z.infer<typeof IdentitySkillDescEntrySchema>
export type IdentitySkillI18n = z.infer<typeof IdentitySkillI18nSchema>
export type IdentityPassiveI18n = z.infer<typeof IdentityPassiveI18nSchema>
export type IdentityI18n = z.infer<typeof IdentityI18nSchema>
