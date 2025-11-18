/**
 * Identity detail data types
 */

export interface IdentityData {
  sinner: string
  grade: number
  HP: number
  minSpeed: number
  maxSpeed: number
  defLV: number
  resist: [number, number, number] // [slash, pierce, blunt]
  stagger: number[]
  traits: string[]
  skills: SkillsData
  passive: PassiveData[]
  sptPassive: PassiveData // Single object in data JSON
}

export interface SkillsData {
  skill1: SkillData[]
  skill2: SkillData[]
  skill3: SkillData[]
  skillDef: SkillData[]
}

export interface SkillData {
  sin: string
  atkType?: string
  quantity: number
  coinEA: string
  LV: number
  upties: {
    '3': UptieData
    '4': UptieData
  }
}

export interface UptieData {
  basePower: number
  coinPower: number
  atkWeight: number
}

export interface PassiveData {
  passiveSin?: string[]
  passiveEA?: number[]
  passiveType?: string
}

export interface IdentityI18n {
  name: string
  skills: SkillsI18nData
  passive: PassiveI18n[]
  sptPassive: PassiveI18n // Single object in i18n JSON
}

export interface SkillsI18nData {
  skill1: SkillI18nData[]
  skill2: SkillI18nData[]
  skill3: SkillI18nData[]
  skillDef: SkillI18nData[]
}

export interface SkillI18nData {
  name: string
  upties: {
    '3': UptieI18nData
    '4': UptieI18nData
  }
}

export interface UptieI18nData {
  desc: string
  coinDescs: string[]
}

export interface PassiveI18n {
  name: string
  desc: string
}

export type Uptie = '3' | '4'

export type ImageVariant = 'gacksung' | 'normal'
