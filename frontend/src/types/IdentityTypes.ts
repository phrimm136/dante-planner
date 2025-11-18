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
  skill1: SkillVariantI18n[]
  skill2: SkillVariantI18n[]
  skill3: SkillVariantI18n[]
  skillDef: SkillVariantI18n[]
}

export interface SkillVariantI18n {
  name: string
  upties: {
    '3': SkillI18nData
    '4': SkillI18nData
  }
}

export interface SkillI18nData {
  desc: string
  coinDescs: string[]
}

export interface PassiveI18n {
  name: string
  desc: string
}

export type Uptie = '3' | '4'

export type ImageVariant = 'gacksung' | 'normal'
