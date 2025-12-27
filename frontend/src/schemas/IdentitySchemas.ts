import { z } from 'zod'
import { AffinitySchema } from './SharedSchemas'

/**
 * Identity Schemas
 *
 * Zod schemas for runtime validation of Identity data structures.
 * These schemas mirror the TypeScript interfaces in types/IdentityTypes.ts.
 */

// HP data schema
export const IdentityHpDataSchema = z.object({
  defaultStat: z.number(),
  incrementByLevel: z.number(),
})

// Resist info schema
export const IdentityResistInfoSchema = z.object({
  SLASH: z.number(),
  PENETRATE: z.number(),
  HIT: z.number(),
})

// Mental condition info schema
export const IdentityMentalConditionInfoSchema = z.object({
  add: z.array(z.string()),
  min: z.array(z.string()),
})

// Skill data entry schema - all fields optional for flexibility
export const IdentitySkillDataEntrySchema = z.object({
  attributeType: z.string().optional(),
  atkType: z.string().optional(),
  targetNum: z.number().optional(),
  mpUsage: z.number().optional(),
  skillLevelCorrection: z.number().optional(),
  defaultValue: z.number().optional(),
  scale: z.number().optional(),
  iconID: z.string().optional(),
})

// Skill data tuple - 4 entries for uptie levels 0-3
export const IdentitySkillDataTupleSchema = z.tuple([
  IdentitySkillDataEntrySchema,
  IdentitySkillDataEntrySchema,
  IdentitySkillDataEntrySchema,
  IdentitySkillDataEntrySchema,
])

// Skill entry schema
export const IdentitySkillEntrySchema = z.object({
  id: z.number(),
  skillData: IdentitySkillDataTupleSchema,
})

// Skills data schema
export const IdentitySkillsDataSchema = z.object({
  skill1: z.array(IdentitySkillEntrySchema),
  skill2: z.array(IdentitySkillEntrySchema),
  skill3: z.array(IdentitySkillEntrySchema),
  skillDef: z.array(IdentitySkillEntrySchema),
})

// Passive condition schema
export const IdentityPassiveConditionSchema = z.object({
  type: z.string(),
  values: z.record(z.string(), z.number()),
})

// Passive list tuple - 4 entries for uptie levels 0-3
export const IdentityPassiveListTupleSchema = z.tuple([
  z.array(z.number()),
  z.array(z.number()),
  z.array(z.number()),
  z.array(z.number()),
])

// Passives data schema
export const IdentityPassivesDataSchema = z.object({
  battlePassiveList: IdentityPassiveListTupleSchema,
  supportPassiveList: IdentityPassiveListTupleSchema,
  conditions: z.record(z.string(), IdentityPassiveConditionSchema),
})

// Main identity detail data schema
export const IdentityDataSchema = z.object({
  updatedDate: z.number(),
  skillKeywordList: z.array(z.string()),
  panicType: z.number(),
  season: z.number(),
  rank: z.number(),
  hp: IdentityHpDataSchema,
  defCorrection: z.number(),
  minSpeedList: z.array(z.number()),
  maxSpeedList: z.array(z.number()),
  unitKeywordList: z.array(z.string()),
  associationList: z.array(z.string()),
  staggerList: z.array(z.number()),
  ResistInfo: IdentityResistInfoSchema,
  mentalConditionInfo: IdentityMentalConditionInfoSchema,
  skills: IdentitySkillsDataSchema,
  passives: IdentityPassivesDataSchema,
})

/**
 * Identity i18n schemas
 */

// Skill description entry schema
export const IdentitySkillDescEntrySchema = z.object({
  desc: z.string().optional(),
  coinDescs: z.array(z.string()).optional(),
})

// Skill i18n schema
export const IdentitySkillI18nSchema = z.object({
  name: z.string(),
  descs: z.array(IdentitySkillDescEntrySchema),
})

// Passive i18n schema
export const IdentityPassiveI18nSchema = z.object({
  name: z.string(),
  desc: z.string(),
})

// Main identity i18n schema
export const IdentityI18nSchema = z.object({
  name: z.string(),
  skills: z.record(z.string(), IdentitySkillI18nSchema),
  passives: z.record(z.string(), IdentityPassiveI18nSchema),
})

/**
 * Identity spec list schemas (for list views)
 */

// Attack type enum for spec list
export const AtkTypeSchema = z.enum(['SLASH', 'PENETRATE', 'HIT'])

// Spec list item schema
export const IdentitySpecListItemSchema = z.object({
  updateDate: z.number(),
  skillKeywordList: z.array(z.string()),
  season: z.number(),
  rank: z.number(),
  unitKeywordList: z.array(z.string()),
  associationList: z.array(z.string()),
  attributeType: z.array(AffinitySchema),
  atkType: z.array(AtkTypeSchema),
})

// Record types for spec and name lists
export const IdentitySpecListSchema = z.record(z.string(), IdentitySpecListItemSchema)
export const IdentityNameListSchema = z.record(z.string(), z.string())
