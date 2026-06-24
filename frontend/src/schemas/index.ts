/**
 * Schema Index
 *
 * Central export point for all Zod validation schemas.
 * Import schemas from this file for runtime validation.
 *
 * @example
 * import { EGOGiftDataSchema } from '@/schemas'
 * const result = EGOGiftDataSchema.safeParse(data)
 */

// Shared schemas
export { SinSchema, PassiveI18nSchema } from './SharedSchemas'

// Color Code schemas
export { ColorCodeMapSchema } from './ColorCodeSchemas'

// Start Gift schemas
export { StartEgoGiftPoolsSchema } from './StartGiftSchemas'

// Battle Keywords schemas
export {
  BattleKeywordEntrySchema,
  BattleKeywordsSchema,
} from './BattleKeywordsSchemas'

// Keyword spec schemas
export {
  BattleKeywordSpecEntrySchema,
  BattleKeywordSpecListSchema,
  BattleKeywordNameListSchema,
} from './KeywordSchemas'

// Skill Tag schemas
export { SkillTagSchema } from './SkillTagSchemas'

// Auth schemas
export { UserSchema } from './AuthSchemas'

// Planner schemas - EXCLUDED from barrel to avoid bundling Tiptap
// Import directly: import { ... } from '@/schemas/PlannerSchemas'

// Note Editor schemas - EXCLUDED from barrel to avoid bundling Tiptap
// Import directly: import { ... } from '@/schemas/NoteEditorSchemas'

// Filter i18n schemas
export {
  SeasonsI18nSchema,
  UnitKeywordsI18nSchema,
} from './FilterSchemas'

// Search mapping schemas
export {
  KeywordMatchSchema,
  UnitKeywordsSchema,
} from './SearchMappingSchemas'

// Panic Info schemas
export {
  PanicInfoEntrySchema,
  PanicInfoSchema,
} from './PanicInfoSchemas'
