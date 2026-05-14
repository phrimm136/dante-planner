/**
 * EGO list data types (merged from specList + nameList)
 */

import type { AtkType, Season, SkillAttributeType } from "@/lib/constants"

export type EGOType = 'ZAYIN' | 'TETH' | 'HE' | 'WAW' | 'ALEPH'

/**
 * Threadspin level type - 1 through 5
 */
export type Threadspin = 1 | 2 | 3 | 4 | 5

/**
 * EGO list item for list/grid views.
 * Name is optional - populated when i18n is loaded, but components should prefer
 * EGOName component for granular Suspense boundaries.
 */
export interface EGOListItem {
  id: string
  /** Optional - populated when i18n is loaded */
  name?: string
  egoType: EGOType
  skillKeywordList: string[]
  battleKeywordList: string[]
  attributeTypes: SkillAttributeType[]
  atkTypes: AtkType[]
  updateDate: number
  season: Season
  maxThreadspin: 4 | 5
}

/**
 * EGO detail data types (from static/data/ego/{id}.json)
 */

/**
 * Skill data entry at each uptie level
 * Can be empty {} for levels where data doesn't change.
 * First non-empty entry contains full base stats, subsequent entries contain overrides.
 */
export interface EGOSkillDataEntry {
  attributeType?: string
  atkType?: string
  targetNum?: number
  mpUsage?: number
  skillLevelCorrection?: number
  defaultValue?: number
  scale?: number
  coinString?: string
}

/**
 * Skill data array — 4 or 5 entries per threadspin levels (per-EGO).
 * The 5-tuple branch matches EGOs with threadspin 5; the 4-tuple branch matches the rest.
 */
export type EGOSkillDataTuple =
  | [
      EGOSkillDataEntry,
      EGOSkillDataEntry,
      EGOSkillDataEntry,
      EGOSkillDataEntry
    ]
  | [
      EGOSkillDataEntry,
      EGOSkillDataEntry,
      EGOSkillDataEntry,
      EGOSkillDataEntry,
      EGOSkillDataEntry
    ]

export interface EGOSkillEntry {
  id: number
  skillData: EGOSkillDataTuple
}

export interface EGOSkillsData {
  awaken: EGOSkillEntry[]
  erosion: EGOSkillEntry[]
}

/**
 * Passive list per threadspin level — 4 or 5 entries (per-EGO).
 * Each element is an array of passive ID strings active at that level.
 */
export type EGOPassiveListTuple =
  | [string[], string[], string[], string[]]
  | [string[], string[], string[], string[], string[]]

export interface EGOPassivesData {
  passiveList: EGOPassiveListTuple
}

export interface EGOData {
  updatedDate: number
  egoType: EGOType
  season: number
  attributeResist: Record<string, number>
  requirements: Record<string, number>
  skills: EGOSkillsData
  passives: EGOPassivesData
  maxThreadspin: 4 | 5
}

/**
 * EGO i18n types (from static/i18n/{lang}/ego/{id}.json)
 */

export interface EGOSkillDescEntry {
  desc?: string
  coinDescs?: string[]
}

export interface EGOSkillI18n {
  name: string
  /** Per-skill lore line; not per threadspin level. Forward-compat — raw EGO data does not yet ship it. */
  flavor?: string
  descs: EGOSkillDescEntry[]
}

export interface EGOPassiveI18n {
  name: string
  desc: string
  /** Lore line shown beneath the passive description. Forward-compat — raw EGO passive data does not yet ship it. */
  flavor?: string
}

export interface EGOI18n {
  name: string
  skills: Record<string, EGOSkillI18n>
  passives: Record<string, EGOPassiveI18n>
}
