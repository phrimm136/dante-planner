import { z } from 'zod'
import { SinSchema, PassiveI18nSchema } from './SharedSchemas'

/**
 * Identity Schemas
 *
 * Zod schemas for runtime validation of Identity data structures.
 * These schemas mirror the TypeScript interfaces in types/IdentityTypes.ts
 * and provide strict runtime validation with comprehensive error collection.
 *
 * MAINTENANCE: When TypeScript interfaces change, regenerate schemas using
 * the shared source generation tooling to maintain synchronization.
 */

// Uptie enum validation
export const UptieSchema = z.enum(['3', '4'])

// Image variant enum validation
export const ImageVariantSchema = z.enum(['gacksung', 'normal'])

// UptieData schema - nested in SkillData
export const UptieDataSchema = z.object({
  basePower: z.number(),
  coinPower: z.number(),
  atkWeight: z.number(),
}).strict()

// PassiveData schema - all fields optional
export const PassiveDataSchema = z.object({
  passiveSin: z.array(SinSchema).optional(),
  passiveEA: z.array(z.number()).optional(),
  passiveType: z.string().optional(),
}).strict()

// SkillData schema - with upties literal keys
export const SkillDataSchema = z.object({
  sin: SinSchema,
  atkType: z.string().optional(),
  quantity: z.number(),
  coinEA: z.string(),
  LV: z.number(),
  upties: z.object({
    '3': UptieDataSchema,
    '4': UptieDataSchema,
  }).strict(),
}).strict()

// SkillsData schema - four skill arrays
export const SkillsDataSchema = z.object({
  skill1: z.array(SkillDataSchema),
  skill2: z.array(SkillDataSchema),
  skill3: z.array(SkillDataSchema),
  skillDef: z.array(SkillDataSchema),
}).strict()

// IdentityData schema - main detail data with resist tuple and variable stagger
export const IdentityDataSchema = z.object({
  sinner: z.string(),
  grade: z.number(),
  HP: z.number(),
  minSpeed: z.number(),
  maxSpeed: z.number(),
  defLV: z.number(),
  resist: z.tuple([z.number(), z.number(), z.number()]), // [slash, pierce, blunt]
  stagger: z.array(z.number()), // Variable length - no constraint
  traits: z.array(z.string()),
  skills: SkillsDataSchema,
  passive: z.array(PassiveDataSchema),
  sptPassive: PassiveDataSchema,
}).strict()

// Identity schema - list item
export const IdentitySchema = z.object({
  id: z.string(),
  name: z.string(),
  star: z.number(),
  sinner: z.string(),
  traits: z.array(z.string()),
  keywords: z.array(z.string()),
}).strict()

// UptieI18nData schema - nested in SkillI18nData
export const UptieI18nDataSchema = z.object({
  desc: z.string(),
  coinDescs: z.array(z.string()),
}).strict()

// SkillI18nData schema - with upties literal keys
export const SkillI18nDataSchema = z.object({
  name: z.string(),
  upties: z.object({
    '3': UptieI18nDataSchema,
    '4': UptieI18nDataSchema,
  }).strict(),
}).strict()

// SkillsI18nData schema - four skill arrays
export const SkillsI18nDataSchema = z.object({
  skill1: z.array(SkillI18nDataSchema),
  skill2: z.array(SkillI18nDataSchema),
  skill3: z.array(SkillI18nDataSchema),
  skillDef: z.array(SkillI18nDataSchema),
}).strict()

// IdentityI18n schema - i18n data
export const IdentityI18nSchema = z.object({
  name: z.string(),
  skills: SkillsI18nDataSchema,
  passive: z.array(PassiveI18nSchema),
  sptPassive: PassiveI18nSchema,
}).strict()

// Record types for spec and name lists
export const IdentitySpecListSchema = z.record(z.string(), IdentityDataSchema)
export const IdentityNameListSchema = z.record(z.string(), z.string())
