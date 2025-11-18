/**
 * EGO list data types
 */

export type EGORank = 'Zayin' | 'Teth' | 'He' | 'Waw' | 'Aleph'

export interface EGO {
  id: string
  name: string
  rank: EGORank
  sin: string
  sinner: string
  keywords: string[]
}

/**
 * EGO detail data types
 */

export interface EGOThreadspinData {
  basePower: number
  coinPower: number
  atkWeight: number
}

export interface EGOSkillData {
  coinEA: string
  atkType: string
  LV: number
  sanityCost: number
  threadspins: {
    '3': EGOThreadspinData[]
    '4': EGOThreadspinData[]
  }
}

export interface EGOData {
  sinner: string
  rank: string
  resitances: number[]
  costs: number[]
  sin: string
  skills: {
    awakening: EGOSkillData
    corrosion?: EGOSkillData
  }
}

export interface EGOThreadspinI18n {
  desc: string
  coinDescs: string[]
}

export interface EGOSkillI18n {
  name: string
  threadspins: {
    '3': EGOThreadspinI18n[]
    '4': EGOThreadspinI18n[]
  }
}

export interface PassiveI18n {
  name: string
  desc: string
}

export interface EGOI18n {
  name: string
  traits: string
  skills: {
    awakening: EGOSkillI18n
    corrosion?: EGOSkillI18n
  }
  passive: PassiveI18n[]
}
