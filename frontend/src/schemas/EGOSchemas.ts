import { z } from 'zod'
import { SinSchema, PassiveI18nSchema } from './SharedSchemas'

/**
 * EGO Schemas
 *
 * Zod schemas for runtime validation of EGO data structures.
 * These schemas mirror the TypeScript interfaces in types/EGOTypes.ts
 * and provide strict runtime validation with comprehensive error collection.
 *
 * MAINTENANCE: When TypeScript interfaces change, regenerate schemas using
 * the shared source generation tooling to maintain synchronization.
 */

// EGORank enum validation - all five rank values
export const EGORankSchema = z.enum(['Zayin', 'Teth', 'He', 'Waw', 'Aleph'])

// EGOThreadspinData schema - nested in EGOSkillData
export const EGOThreadspinDataSchema = z.object({
  basePower: z.number(),
  coinPower: z.number(),
  atkWeight: z.number(),
}).strict()

// EGOSkillData schema - with threadspins literal keys
export const EGOSkillDataSchema = z.object({
  coinEA: z.string(),
  atkType: z.string(),
  LV: z.number(),
  sanityCost: z.number(),
  threadspins: z.object({
    '3': z.array(EGOThreadspinDataSchema),
    '4': z.array(EGOThreadspinDataSchema),
  }).strict(),
}).strict()

// EGOData schema - main detail data with array length constraints
export const EGODataSchema = z.object({
  sinner: z.string(),
  rank: EGORankSchema,
  resistances: z.array(z.number()).length(7), // Exactly 7 sin type resistances
  costs: z.array(z.number()).length(7), // Exactly 7 sin costs
  sin: SinSchema,
  skills: z.object({
    awakening: EGOSkillDataSchema,
    corrosion: EGOSkillDataSchema.optional(), // Optional corrosion skill
  }).strict(),
}).strict()

// EGO schema - list item
export const EGOSchema = z.object({
  id: z.string(),
  name: z.string(),
  rank: EGORankSchema,
  sin: SinSchema,
  sinner: z.string(),
  keywords: z.array(z.string()),
}).strict()

// EGOThreadspinI18n schema - nested in EGOSkillI18n
export const EGOThreadspinI18nSchema = z.object({
  desc: z.string(),
  coinDescs: z.array(z.string()),
}).strict()

// EGOSkillI18n schema - with threadspins literal keys
export const EGOSkillI18nSchema = z.object({
  name: z.string(),
  threadspins: z.object({
    '3': z.array(EGOThreadspinI18nSchema),
    '4': z.array(EGOThreadspinI18nSchema),
  }).strict(),
}).strict()

// EGOI18n schema - i18n data
export const EGOI18nSchema = z.object({
  name: z.string(),
  traits: z.string(),
  skills: z.object({
    awakening: EGOSkillI18nSchema,
    corrosion: EGOSkillI18nSchema.optional(), // Optional corrosion skill
  }).strict(),
  passive: z.array(PassiveI18nSchema),
}).strict()

// Record types for spec and name lists
export const EGOSpecListSchema = z.record(z.string(), EGODataSchema)
export const EGONameListSchema = z.record(z.string(), z.string())
