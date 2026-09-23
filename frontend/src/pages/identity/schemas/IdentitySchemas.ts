import { z } from 'zod'
import { AffinitySchema, ATK_TYPES, DEF_TYPES } from '@/shared/gameData'
import {
  SkillDescEntrySchema,
  PassiveIdSchema,
  IdentityIdSchema,
  skillMapWithRetiredKeys,
} from '@/shared/gameData'

export const IdentityHpDataSchema = z.object({
  defaultStat: z.number(),
  incrementByLevel: z.number(),
})

export const IdentityResistInfoSchema = z.object({
  SLASH: z.number(),
  PENETRATE: z.number(),
  HIT: z.number(),
})

export const IdentityMentalConditionInfoSchema = z.object({
  add: z.array(z.string()),
  min: z.array(z.string()),
})

export const IdentitySkillsDataSchema = skillMapWithRetiredKeys([
  'skill1',
  'skill2',
  'skill3',
  'skillDef',
])

export const IdentityPassiveConditionSchema = z.object({
  type: z.string(),
  values: z.record(z.string(), z.number()),
})

export const IdentityPassiveListTupleSchema = z.tuple([
  z.array(PassiveIdSchema),
  z.array(PassiveIdSchema),
  z.array(PassiveIdSchema),
  z.array(PassiveIdSchema),
])

export const IdentityPassivesDataSchema = z.object({
  battlePassiveList: IdentityPassiveListTupleSchema,
  supportPassiveList: IdentityPassiveListTupleSchema,
  conditions: z.record(z.string(), IdentityPassiveConditionSchema),
})

export const IdentityDataSchema = z.object({
  updatedDate: z.number(),
  skillKeywordList: z.array(z.string()),
  battleKeywordList: z.array(z.string()),
  panicType: z.string(),
  season: z.number(),
  rank: z.number(),
  hp: IdentityHpDataSchema,
  defCorrection: z.number(),
  minSpeedList: z.array(z.number()),
  maxSpeedList: z.array(z.number()),
  unitKeywordList: z.array(z.string()),
  staggerList: z.array(z.number()),
  ResistInfo: IdentityResistInfoSchema,
  mentalConditionInfo: IdentityMentalConditionInfoSchema,
  skills: IdentitySkillsDataSchema,
  passives: IdentityPassivesDataSchema,
})

export const IdentitySkillDescEntrySchema = SkillDescEntrySchema

// `flavor` is a per-skill lore line (not per uptie) — raw game data ships the
// same flavor on every level entry, so we collapse it at the skill level.
export const IdentitySkillI18nSchema = z.object({
  name: z.string(),
  flavor: z.string().optional(),
  descs: z.array(IdentitySkillDescEntrySchema),
})

export const IdentityPassiveI18nSchema = z.object({
  name: z.string(),
  desc: z.string(),
  flavor: z.string().optional(),
})

export const IdentityI18nSchema = z.object({
  name: z.string(),
  skills: z.record(z.string(), IdentitySkillI18nSchema),
  passives: z.record(z.string(), IdentityPassiveI18nSchema),
})

export const AtkTypeSchema = z.enum(ATK_TYPES)

export const DefenseTypeSchema = z.enum(DEF_TYPES)

export const IdentitySpecSchema = z.object({
  updateDate: z.number(),
  skillKeywordList: z.array(z.string()),
  battleKeywordList: z.array(z.string()),
  season: z.number(),
  rank: z.number(),
  unitKeywordList: z.array(z.string()),
  attributeType: z.array(AffinitySchema),
  atkType: z.array(AtkTypeSchema),
  defenseType: z.array(DefenseTypeSchema),
})

export const IdentitySpecListSchema = z.record(IdentityIdSchema, IdentitySpecSchema)
/** Keys stay unbranded: the game ships i18n-only ids (e.g. 40501) with no spec entry. */
export const IdentityNameListSchema = z.record(z.string(), z.string())
