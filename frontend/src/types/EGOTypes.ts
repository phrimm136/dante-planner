/**
 * EGO list data types (merged from specList + nameList)
 */

export type EgoType = 'ZAYIN' | 'TETH' | 'HE' | 'WAW' | 'ALEPH'

/**
 * Threadspin level type - 1 through 5
 */
export type Threadspin = 1 | 2 | 3 | 4 | 5

export interface EGO {
  id: string
  name: string
  rank: EgoType
  attributeType: string[]
  skillKeywordList: string[]
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
}

/**
 * Skill data array for all 4 uptie levels [0, 1, 2, 3]
 */
export type EGOSkillDataTuple = [
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
 * Passive list per uptie level [0, 1, 2, 3]
 * Each element is an array of passive ID strings active at that uptie
 */
export type EGOPassiveListTuple = [string[], string[], string[], string[]]

export interface EGOPassivesData {
  passiveList: EGOPassiveListTuple
}

export interface EGOData {
  updatedDate: number
  egoType: EgoType
  season: number
  attributeResist: Record<string, number>
  requirements: Record<string, number>
  skills: EGOSkillsData
  passives: EGOPassivesData
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
  descs: EGOSkillDescEntry[]
}

export interface EGOPassiveI18n {
  name: string
  desc: string
}

export interface EGOI18n {
  name: string
  skills: Record<string, EGOSkillI18n>
  passives: Record<string, EGOPassiveI18n>
}
