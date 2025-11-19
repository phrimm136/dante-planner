/**
 * Schema Index
 *
 * Central export point for all Zod validation schemas.
 * Import schemas from this file for runtime validation.
 *
 * @example
 * import { IdentityDataSchema, EGODataSchema } from '@/schemas'
 * const result = IdentityDataSchema.safeParse(data)
 */

// Shared schemas
export { SinSchema, PassiveI18nSchema } from './SharedSchemas'

// Identity schemas
export {
  UptieSchema,
  ImageVariantSchema,
  UptieDataSchema,
  PassiveDataSchema,
  SkillDataSchema,
  SkillsDataSchema,
  IdentityDataSchema,
  IdentitySchema,
  UptieI18nDataSchema,
  SkillI18nDataSchema,
  SkillsI18nDataSchema,
  IdentityI18nSchema,
  IdentitySpecListSchema,
  IdentityNameListSchema,
} from './IdentitySchemas'

// EGO schemas
export {
  EGORankSchema,
  EGOThreadspinDataSchema,
  EGOSkillDataSchema,
  EGODataSchema,
  EGOSchema,
  EGOThreadspinI18nSchema,
  EGOSkillI18nSchema,
  EGOI18nSchema,
  EGOSpecListSchema,
  EGONameListSchema,
} from './EGOSchemas'

// EGO Gift schemas
export {
  EGOGiftSpecSchema,
  EGOGiftDataSchema,
  EGOGiftI18nSchema,
  EGOGiftSchema,
  EGOGiftSpecListSchema,
  EGOGiftNameListSchema,
} from './EGOGiftSchemas'
