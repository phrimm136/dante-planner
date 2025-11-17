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
  passsiveEA?: number[]
  passiveType?: string
}

export interface IdentityI18n {
  Name: string
  Character: string
  Skills: {
    Uptie3: SkillI18nSlots
    Uptie4: SkillI18nSlots
  }
  Passive: PassiveI18n[]
  SptPassive: PassiveI18n[]
}

export interface SkillI18nSlots {
  Skill1: SkillI18nData[]
  Skill2: SkillI18nData[]
  Skill3: SkillI18nData[]
}

export interface SkillI18nData {
  Desc: string
  CoinDescs: string[]
}

export interface PassiveI18n {
  Name: string
  Desc: string
}

export type ImageVariant = 'gacksung' | 'normal'
