import { z } from 'zod'
import { AffinitySchema } from './SharedSchemas'

/**
 * EGO Schemas
 *
 * Zod schemas for runtime validation of EGO data structures.
 * These schemas mirror the TypeScript interfaces in types/EGOTypes.ts.
 */

// EGO type enum (ZAYIN, TETH, HE, WAW, ALEPH)
export const EgoTypeSchema = z.enum(['ZAYIN', 'TETH', 'HE', 'WAW', 'ALEPH'])

// Skill data entry schema - all fields optional for flexibility
export const EGOSkillDataEntrySchema = z.object({
  attributeType: z.string().optional(),
  atkType: z.string().optional(),
  targetNum: z.number().optional(),
  mpUsage: z.number().optional(),
  skillLevelCorrection: z.number().optional(),
  defaultValue: z.number().optional(),
  scale: z.number().optional(),
})

// Skill data tuple - 4 entries for uptie levels 0-3
export const EGOSkillDataTupleSchema = z.tuple([
  EGOSkillDataEntrySchema,
  EGOSkillDataEntrySchema,
  EGOSkillDataEntrySchema,
  EGOSkillDataEntrySchema,
])

// Skill entry schema
export const EGOSkillEntrySchema = z.object({
  id: z.number(),
  skillData: EGOSkillDataTupleSchema,
})

// Skills data schema
export const EGOSkillsDataSchema = z.object({
  awaken: z.array(EGOSkillEntrySchema),
  erosion: z.array(EGOSkillEntrySchema),
})

// Passive list tuple - 4 entries for uptie levels 0-3
// Each element is an array of passive ID strings active at that uptie
export const EGOPassiveListTupleSchema = z.tuple([
  z.array(z.string()),
  z.array(z.string()),
  z.array(z.string()),
  z.array(z.string()),
])

// Passives data schema
export const EGOPassivesDataSchema = z.object({
  passiveList: EGOPassiveListTupleSchema,
})

// Main EGO detail data schema
export const EGODataSchema = z.object({
  updatedDate: z.number(),
  egoType: EgoTypeSchema,
  season: z.number(),
  attributeResist: z.record(z.string(), z.number()),
  requirements: z.record(z.string(), z.number()),
  skills: EGOSkillsDataSchema,
  passives: EGOPassivesDataSchema,
})

/**
 * EGO i18n schemas
 */

// Skill description entry schema
export const EGOSkillDescEntrySchema = z.object({
  desc: z.string().optional(),
  coinDescs: z.array(z.string()).optional(),
})

// Skill i18n schema
export const EGOSkillI18nSchema = z.object({
  name: z.string(),
  descs: z.array(EGOSkillDescEntrySchema),
})

// Passive i18n schema
export const EGOPassiveI18nSchema = z.object({
  name: z.string(),
  desc: z.string(),
})

// Main EGO i18n schema
export const EGOI18nSchema = z.object({
  name: z.string(),
  skills: z.record(z.string(), EGOSkillI18nSchema),
  passives: z.record(z.string(), EGOPassiveI18nSchema),
})

/**
 * EGO list schemas (for list views)
 */

// EGO list item schema
export const EGOSchema = z.object({
  id: z.string(),
  name: z.string(),
  rank: EgoTypeSchema,
  attributeType: z.array(z.string()),
  skillKeywordList: z.array(z.string()),
})

// Attack type enum for spec list
export const EGOAtkTypeSchema = z.enum(['SLASH', 'PENETRATE', 'HIT'])

// Spec list item schema
export const EGOSpecListItemSchema = z.object({
  updateDate: z.number(),
  skillKeywordList: z.array(z.string()),
  season: z.number(),
  egoType: EgoTypeSchema,
  requirements: z.record(z.string(), z.number()),
  attributeType: z.array(AffinitySchema),
  atkType: z.array(EGOAtkTypeSchema),
})

// Record types for spec and name lists
export const EGOSpecListSchema = z.record(z.string(), EGOSpecListItemSchema)
export const EGONameListSchema = z.record(z.string(), z.string())
