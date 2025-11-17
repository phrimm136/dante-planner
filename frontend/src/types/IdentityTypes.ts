/**
 * Identity detail data types
 */

export interface IdentityData {
  grade: number
  HP: number
  minSpeed: number
  maxSpeed: number
  defLV: string
  resist: [number, number, number] // [slash, pierce, blunt]
  stagger: number[]
  traits: string[]
  skills: SkillsData
  passive: PassiveData[]
  sptPassive: PassiveData[]
}

export interface SkillsData {
  uptie3: SkillSlots
  uptie4: SkillSlots
}

export interface SkillSlots {
  skill1: SkillData[]
  skill2: SkillData[]
  skill3: SkillData[]
  skillDef: SkillData[]
}

export interface SkillData {
  basePower: number
  coinPower: number
  coinEA: string
  sin: string
  atkType?: string
  atkWeight: number
  LV: number
  quantity: number
}

export interface PassiveData {
  passiveSin?: string[]
  passiveEA?: number[]
  passiveType?: string
}

export interface IdentityI18n {
  name: string
  character: string
  skills: {
    uptie3: SkillI18nSlots
    uptie4: SkillI18nSlots
  }
  passive: PassiveI18n[]
  sptPassive: PassiveI18n[]
}

export interface SkillI18nSlots {
  skill1: SkillI18nData[]
  skill2: SkillI18nData[]
  skill3: SkillI18nData[]
  skillDef: SkillI18nData[]
}

export interface SkillI18nData {
  name: string
  desc: string
  coinDescs: string[]
}

export interface PassiveI18n {
  name: string
  desc: string
}

export type ImageVariant = 'gacksung' | 'normal'
