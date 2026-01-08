import type { AtkType, Season, SkillAttributeType } from '@/lib/constants'

/**
 * Identity list item for list/grid views.
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
  /** Skill attribute types (affinities) - e.g. ['AZURE', 'VIOLET', 'AMBER'] */
  attributeTypes: SkillAttributeType[]
  /** Attack types per skill - e.g. ['SLASH', 'PENETRATE', 'HIT'] */
  atkTypes: AtkType[]
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
  /** Season identifier (0=Standard, 1-6=Seasons, 8000=Collab, 9101+=Walpurgis) */
  season: Season
}

/**
 * Uptie level type - 1 through 4
 */
export type Uptie = 1 | 2 | 3 | 4

/**
 * Identity detail data types (from static/data/identity/{id}.json)
 */

export interface IdentityHpData {
  defaultStat: number
  incrementByLevel: number
}

export interface IdentityResistInfo {
  SLASH: number
  PENETRATE: number
  HIT: number
}

export interface IdentityMentalConditionInfo {
  add: string[]
  min: string[]
}

/**
 * Skill data entry at each uptie level
 * Can be empty {} for levels where skill isn't available yet.
 * First non-empty entry contains full base stats, subsequent entries contain overrides.
 * All fields optional to accommodate both empty and partial entries.
 */
export interface IdentitySkillDataEntry {
  attributeType?: string
  atkType?: string
  targetNum?: number
  mpUsage?: number
  skillLevelCorrection?: number
  defaultValue?: number
  scale?: number
  iconID?: string
  /** Skill tier (1-3) determines frame appearance */
  skillTier?: number
}

/**
 * Skill data array for all 4 uptie levels [0, 1, 2, 3]
 */
export type IdentitySkillDataTuple = [
  IdentitySkillDataEntry,
  IdentitySkillDataEntry,
  IdentitySkillDataEntry,
  IdentitySkillDataEntry
]

export interface IdentitySkillEntry {
  id: number
  /** Text ID for i18n lookup (may differ from id for skill variants). Falls back to id if not present. */
  textID?: number
  skillData: IdentitySkillDataTuple
}

export interface IdentitySkillsData {
  skill1: IdentitySkillEntry[]
  skill2: IdentitySkillEntry[]
  skill3: IdentitySkillEntry[]
  skillDef: IdentitySkillEntry[]
}

export interface IdentityPassiveCondition {
  type: string
  values: Record<string, number>
}

/**
 * Passive lists per uptie level [0, 1, 2, 3]
 * Each element is an array of passive IDs active at that uptie
 */
export type IdentityPassiveListTuple = [number[], number[], number[], number[]]

export interface IdentityPassivesData {
  battlePassiveList: IdentityPassiveListTuple
  supportPassiveList: IdentityPassiveListTuple
  conditions: Record<string, IdentityPassiveCondition>
}

export interface IdentityData {
  updateDate: number
  skillKeywordList: string[]
  panicType: number
  season: number
  rank: number
  hp: IdentityHpData
  defCorrection: number
  minSpeedList: number[]
  maxSpeedList: number[]
  unitKeywordList: string[]
  staggerList: number[]
  ResistInfo: IdentityResistInfo
  mentalConditionInfo: IdentityMentalConditionInfo
  skills: IdentitySkillsData
  passives: IdentityPassivesData
}

/**
 * Identity i18n types (from static/i18n/{lang}/identity/{id}.json)
 */

export interface IdentitySkillDescEntry {
  desc?: string
  coinDescs?: string[]
}

export interface IdentitySkillI18n {
  name: string
  descs: IdentitySkillDescEntry[]
}

export interface IdentityPassiveI18n {
  name: string
  desc: string
}

export interface IdentityI18n {
  name: string
  skills: Record<string, IdentitySkillI18n>
  passives: Record<string, IdentityPassiveI18n>
}
