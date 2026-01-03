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
  IdentityDataSchema,
  IdentityI18nSchema,
  IdentitySpecListSchema,
  IdentityNameListSchema,
} from './IdentitySchemas'

// EGO schemas
export {
  EGODataSchema,
  EGOI18nSchema,
  EGOSpecListSchema,
  EGONameListSchema,
} from './EGOSchemas'

// EGO Gift schemas
export {
  EGOGiftSpecSchema,
  EGOGiftDataSchema,
  EGOGiftI18nSchema,
  EGOGiftListItemSchema,
  EGOGiftSpecListSchema,
  EGOGiftNameListSchema,
} from './EGOGiftSchemas'

// Color Code schemas
export { ColorCodeMapSchema } from './ColorCodeSchemas'

// Start Gift schemas
export { StartEgoGiftPoolsSchema } from './StartGiftSchemas'

// EGO Gift Observation schemas
export {
  EGOGiftObservationCostSchema,
  EGOGiftObservationDataSchema,
} from './EGOGiftObservationSchemas'

// Battle Keywords schemas
export {
  BattleKeywordEntrySchema,
  BattleKeywordsSchema,
} from './BattleKeywordsSchemas'

// Skill Tag schemas
export { SkillTagSchema } from './SkillTagSchemas'

// Auth schemas
export { UserSchema } from './AuthSchemas'

// Theme Pack schemas
export {
  ExceptionConditionSchema,
  ThemePackConfigSchema,
  ThemePackEntrySchema,
  ThemePackListSchema,
  ThemePackI18nEntrySchema,
  ThemePackI18nSchema,
} from './ThemePackSchemas'

// Planner schemas - EXCLUDED from barrel to avoid bundling Tiptap
// Import directly: import { ... } from '@/schemas/PlannerSchemas'

// Note Editor schemas - EXCLUDED from barrel to avoid bundling Tiptap
// Import directly: import { ... } from '@/schemas/NoteEditorSchemas'

// Filter i18n schemas
export {
  SeasonsI18nSchema,
  AssociationsI18nSchema,
} from './FilterSchemas'

// Search mapping schemas
export {
  KeywordMatchSchema,
  UnitKeywordsSchema,
} from './SearchMappingSchemas'

// Extraction schemas
export {
  ExtractionTargetTypeSchema,
  ActiveRateTableSchema,
  BannerModifiersSchema,
  ExtractionTargetSchema,
  ExtractionInputSchema,
  TargetProbabilitySchema,
  ExtractionResultSchema,
  EffectiveRatesSchema,
} from './ExtractionSchemas'

// Panic Info schemas
export {
  PanicInfoEntrySchema,
  PanicInfoSchema,
} from './PanicInfoSchemas'
