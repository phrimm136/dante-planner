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

// Planner schemas
export {
  // ID validation schemas
  IdentityIdSchema,
  EGOIdSchema,
  GiftIdSchema,
  // Enum schemas
  PlannerStatusSchema,
  MDCategorySchema,
  DungeonIdxSchema,
  // Content schemas
  SerializableFloorSelectionSchema,
  SerializableNoteContentSchema,
  PlannerMetadataSchema,
  PlannerContentSchema,
  SaveablePlannerSchema,
  PlannerSummarySchema,
  // Serialization helpers
  serializeSets,
  deserializeSets,
} from './PlannerSchemas'

// Note Editor schemas
export {
  TiptapMarkSchema,
  JSONContentSchema,
  NoteContentSchema,
  NoteSectionsSchema,
  NoteImageSchema,
  createEmptyNoteContent,
} from './NoteEditorSchemas'
