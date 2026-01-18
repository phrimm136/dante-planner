import { z } from 'zod'
import { DUNGEON_IDX, MAX_LEVEL, MD_CATEGORIES, RR_CATEGORIES, PLANNER_TYPES } from '@/lib/constants'
import { JSONContentSchema } from './NoteEditorSchemas'
import type { SerializableFloorSelection, SaveablePlanner, MDPlannerContent, RRPlannerContent } from '@/types/PlannerTypes'
import { EgoTypeSchema } from './EGOSchemas'

/**
 * Planner Schemas
 *
 * Zod schemas for runtime validation of planner data structures.
 * These schemas mirror the TypeScript interfaces in types/PlannerTypes.ts
 * and provide strict runtime validation for IndexedDB storage.
 *
 * Also includes serialization helpers for converting between
 * page state (with Sets) and storage format (with arrays).
 */

// ============================================================================
// ID Pattern Schemas
// ============================================================================

/**
 * Identity ID pattern: 1{01-12}{:2}
 * Examples: 10101, 10102, 11212
 * Format: 1 + sinner index (01-12, 2 digits) + identity index (2+ digits)
 */
export const IdentityIdSchema = z.string().regex(
  /^1(0[1-9]|1[0-2])\d{2,}$/,
  'Identity ID must match pattern 1{01-12}{2+ digits}'
)

/**
 * EGO ID pattern: 2{01-12}{:2}
 * Examples: 20101, 20102, 21212
 * Format: 2 + sinner index (01-12, 2 digits) + EGO index (2+ digits)
 */
export const EGOIdSchema = z.string().regex(
  /^2(0[1-9]|1[0-2])\d{2,}$/,
  'EGO ID must match pattern 2{01-12}{2+ digits}'
)

/**
 * Gift ID pattern: {1, 2, or empty}{4-digit starting with 9}
 * Examples: 9001, 9999, 19001, 29001
 * Format: optional prefix (1 or 2) + 9 + 3 digits
 */
export const GiftIdSchema = z.string().regex(
  /^[12]?9\d{3}$/,
  'Gift ID must match pattern {1|2|empty}9{3 digits}'
)

/**
 * Theme pack ID pattern: {4-digit}
 * Examples: 1001, 1122, 1508
 */
export const ThemePackSchema = z.string().regex(
  /^\d{4}$/,
  'Theme Pack Id must match pattern {4 digits}'
)

// ============================================================================
// Enum Schemas
// ============================================================================

/**
 * Planner status schema - 'draft' or 'saved'
 */
export const PlannerStatusSchema = z.enum(['draft', 'saved'])

/**
 * MD Category schema - '5F', '10F', or '15F'
 */
export const MDCategorySchema = z.enum(MD_CATEGORIES)

/**
 * RR Category schema - placeholder for future implementation
 */
export const RRCategorySchema = z.enum(RR_CATEGORIES)

/**
 * Dungeon index schema - 0, 1, or 3 (no 2)
 * Matches DUNGEON_IDX constant values
 */
export const DungeonIdxSchema = z.union([
  z.literal(DUNGEON_IDX.NORMAL),
  z.literal(DUNGEON_IDX.HARD),
  z.literal(DUNGEON_IDX.EXTREME),
])

// ============================================================================
// Deck-Related Schemas
// ============================================================================

/**
 * Uptie tier schema - 1, 2, 3, or 4
 */
const UptieTierSchema = z.union([
  z.literal(1),
  z.literal(2),
  z.literal(3),
  z.literal(4),
])

/**
 * Threadspin tier schema - 1, 2, 3, or 4
 */
const ThreadspinTierSchema = z.union([
  z.literal(1),
  z.literal(2),
  z.literal(3),
  z.literal(4),
])

/**
 * Equipped identity schema with validated ID and level
 */
const EquippedIdentitySchema = z.object({
  id: IdentityIdSchema,
  uptie: UptieTierSchema,
  level: z.number().int().min(1).max(MAX_LEVEL),
}).strict()

/**
 * Equipped EGO schema with validated ID
 */
const EquippedEGOSchema = z.object({
  id: EGOIdSchema,
  threadspin: ThreadspinTierSchema,
}).strict()

/**
 * EGO slots schema - Record keyed by EGO type (ZAYIN, TETH, etc.)
 */
const EGOSlotsSchema = z.record(EgoTypeSchema, EquippedEGOSchema)

/**
 * Skill EA state schema - Record keyed by skill slot (0, 1, 2)
 */
const SkillEAStateSchema = z.record(z.string(), z.number())

/**
 * Sinner equipment schema
 */
const SinnerEquipmentSchema = z.object({
  identity: EquippedIdentitySchema,
  egos: EGOSlotsSchema,
}).strict()

// ============================================================================
// Floor Selection Schemas (Draft vs Save)
// ============================================================================

/**
 * Base floor selection schema for drafts
 * Allows null themePackId for incomplete selections
 */
export const FloorSelectionDraftSchema = z.object({
  /** Selected theme pack ID, null if none selected */
  themePackId: ThemePackSchema.nullable(),
  /** Selected difficulty for this floor */
  difficulty: DungeonIdxSchema,
  /** Selected gift IDs as array (serialized from Set) - validated as gift IDs */
  giftIds: z.array(GiftIdSchema),
}).strict()

/**
 * Strict floor selection schema for saves
 * Requires themePackId to be set (no null allowed)
 */
export const FloorSelectionSaveSchema = FloorSelectionDraftSchema.extend({
  /** Selected theme pack ID - REQUIRED for save */
  themePackId: ThemePackSchema,
})

/** Alias for backwards compatibility */
export const SerializableFloorSelectionSchema = FloorSelectionDraftSchema

// ============================================================================
// Note Content Schemas
// ============================================================================

/**
 * Serializable note content schema
 * Wraps Tiptap's JSONContent for storage
 */
export const SerializableNoteContentSchema = z.object({
  /** Rich text content as Tiptap JSONContent */
  content: JSONContentSchema,
}).strict()

// ============================================================================
// Metadata & Content Schemas
// ============================================================================

/**
 * Planner type schema - MIRROR_DUNGEON or REFRACTED_RAILWAY
 */
export const PlannerTypeSchema = z.enum(PLANNER_TYPES)

/**
 * Planner metadata schema
 * Contains tracking and identification data
 */
export const PlannerMetadataSchema = z.object({
  /** Unique identifier (UUID v4) */
  id: z.uuid(),
  /** Planner title (identification, not game state) */
  title: z.string(),
  /** Current save status */
  status: PlannerStatusSchema,
  /** Schema version for data format migration support (1, 2, ...) */
  schemaVersion: z.number().int().positive(),
  /** Game content version (e.g., 6 for MD6, 5 for RR5) */
  contentVersion: z.number().int().positive(),
  /** Type of planner (MIRROR_DUNGEON, REFRACTED_RAILWAY) */
  plannerType: PlannerTypeSchema,
  /** Server sync version for optimistic locking (starts at 1) */
  syncVersion: z.number().int().positive().default(1),
  /** ISO 8601 timestamp when planner was first created */
  createdAt: z.iso.datetime(),
  /** ISO 8601 timestamp when planner was last modified */
  lastModifiedAt: z.iso.datetime(),
  /** ISO 8601 timestamp when planner was explicitly saved */
  savedAt: z.iso.datetime().nullable(),
  /** Device identifier for local storage namespacing */
  deviceId: z.string(),
  /** Whether planner is published to Gesellschaft */
  published: z.boolean().optional(),
}).strict()

// ============================================================================
// Config Schemas (Discriminated Union)
// ============================================================================

/**
 * Mirror Dungeon config schema - discriminated by type field
 */
export const MDConfigSchema = z.object({
  /** Discriminator for type narrowing */
  type: z.literal('MIRROR_DUNGEON'),
  /** MD category (5F, 10F, 15F) */
  category: MDCategorySchema,
}).strict()

/**
 * Refracted Railway config schema - discriminated by type field
 */
export const RRConfigSchema = z.object({
  /** Discriminator for type narrowing */
  type: z.literal('REFRACTED_RAILWAY'),
  /** RR category (placeholder) */
  category: RRCategorySchema,
}).strict()

/**
 * Planner config schema using discriminated union
 * Discriminates on 'type' field for type-safe validation
 */
export const PlannerConfigDiscriminatedSchema = z.discriminatedUnion('type', [
  MDConfigSchema,
  RRConfigSchema,
])

// ============================================================================
// Content Schemas
// ============================================================================

/**
 * Base MD planner content fields (shared between draft and save)
 * Note: title is in PlannerMetadata, category is in PlannerConfig
 */
const MDPlannerContentBaseFields = {
  /** Selected planner keywords (serialized from Set) */
  selectedKeywords: z.array(z.string()),
  /** Selected start buff IDs (serialized from Set<number>) */
  selectedBuffIds: z.array(z.number()),
  /** Currently selected gift keyword filter */
  selectedGiftKeyword: z.string().nullable(),
  /** Selected start gift IDs (serialized from Set) - validated as gift IDs */
  selectedGiftIds: z.array(GiftIdSchema),
  /** Observation gift IDs (serialized from Set) - validated as gift IDs */
  observationGiftIds: z.array(GiftIdSchema),
  /** Comprehensive gift IDs with enhancement encoding (serialized from Set) - validated as gift IDs */
  comprehensiveGiftIds: z.array(GiftIdSchema),
  /** Equipment configuration per sinner */
  equipment: z.record(z.string(), SinnerEquipmentSchema),
  /** Deployment order as array of sinner indices */
  deploymentOrder: z.array(z.number().int().min(0).max(11)),
  /** Skill EA state per sinner */
  skillEAState: z.record(z.string(), SkillEAStateSchema),
  /** Section notes keyed by section identifier */
  sectionNotes: z.record(z.string(), SerializableNoteContentSchema),
} as const

/**
 * MD Draft planner content schema
 * Allows incomplete data (nullable themePackId in floor selections)
 */
export const MDPlannerContentDraftSchema = z.object({
  ...MDPlannerContentBaseFields,
  /** Floor theme selections - nullable themePackId allowed */
  floorSelections: z.array(FloorSelectionDraftSchema),
}).strict()

/**
 * MD Save planner content schema
 * Requires complete data (themePackId required in floor selections)
 */
export const MDPlannerContentSaveSchema = z.object({
  ...MDPlannerContentBaseFields,
  /** Floor theme selections - themePackId REQUIRED */
  floorSelections: z.array(FloorSelectionSaveSchema),
}).strict()

/**
 * RR planner content schema - placeholder for future implementation
 * Note: title is in PlannerMetadata
 */
export const RRPlannerContentDraftSchema = z.object({}).strict()

/**
 * RR save planner content schema (same as draft for now)
 */
export const RRPlannerContentSaveSchema = RRPlannerContentDraftSchema

// Backwards compatibility aliases
/** @deprecated Use MDPlannerContentDraftSchema instead */
export const PlannerContentDraftSchema = MDPlannerContentDraftSchema
/** @deprecated Use MDPlannerContentSaveSchema instead */
export const PlannerContentSaveSchema = MDPlannerContentSaveSchema
/** @deprecated Use MDPlannerContentDraftSchema instead */
export const PlannerContentSchema = MDPlannerContentDraftSchema

// ============================================================================
// Complete Planner Schemas (Draft vs Save)
// ============================================================================

/**
 * Draft planner schema with config layer
 * Allows incomplete data for auto-save during editing
 */
export const DraftPlannerSchema = z.object({
  /** Planner metadata (id, status, timestamps, etc.) */
  metadata: PlannerMetadataSchema,
  /** Planner config (type discriminator and category) */
  config: PlannerConfigDiscriminatedSchema,
  /** Planner content - allows incomplete floor selections */
  content: z.record(z.string(), z.unknown()),
}).strict()

/**
 * Save planner schema with config layer
 * Requires complete data for explicit save operations
 */
export const SavePlannerSchema = z.object({
  /** Planner metadata (id, status, timestamps, etc.) */
  metadata: PlannerMetadataSchema,
  /** Planner config (type discriminator and category) */
  config: PlannerConfigDiscriminatedSchema,
  /** Planner content - requires complete data */
  content: z.record(z.string(), z.unknown()),
}).strict()

/**
 * Default saveable planner schema (draft mode for backwards compatibility)
 */
export const SaveablePlannerSchema = DraftPlannerSchema

// ============================================================================
// Two-Step Validation Functions
// ============================================================================

/**
 * Validate a SaveablePlanner with two-step validation:
 * 1. Validate base structure (metadata + config)
 * 2. Validate content based on config.type discriminator
 *
 * This is necessary because Zod's discriminatedUnion cannot directly
 * validate content based on a sibling field (config.type).
 *
 * @param data - Unknown data to validate
 * @param mode - 'draft' allows incomplete data, 'save' requires complete data
 * @returns Validated SaveablePlanner
 * @throws ZodError if validation fails
 *
 * @example
 * try {
 *   const planner = validateSaveablePlanner(rawData, 'draft')
 *   // planner is now typed as SaveablePlanner
 * } catch (error) {
 *   if (error instanceof z.ZodError) {
 *     console.error('Validation failed:', error.issues)
 *   }
 * }
 */
export function validateSaveablePlanner(
  data: unknown,
  mode: 'draft' | 'save' = 'draft'
): SaveablePlanner {
  // Step 1: Validate base structure with config
  const base = z.object({
    metadata: PlannerMetadataSchema,
    config: PlannerConfigDiscriminatedSchema,
    content: z.record(z.string(), z.unknown()),
  }).strict().parse(data)

  // Step 2: Validate content based on config.type
  let content: MDPlannerContent | RRPlannerContent

  if (base.config.type === 'MIRROR_DUNGEON') {
    const contentSchema = mode === 'save'
      ? MDPlannerContentSaveSchema
      : MDPlannerContentDraftSchema
    content = contentSchema.parse(base.content) as MDPlannerContent
  } else {
    // REFRACTED_RAILWAY
    const contentSchema = mode === 'save'
      ? RRPlannerContentSaveSchema
      : RRPlannerContentDraftSchema
    content = contentSchema.parse(base.content) as RRPlannerContent
  }

  return {
    metadata: base.metadata,
    config: base.config,
    content,
  } as SaveablePlanner
}

/**
 * Validate a SaveablePlanner for saving (stricter validation)
 * Convenience wrapper for validateSaveablePlanner with mode='save'
 */
export function validateSaveablePlannerForSave(data: unknown): SaveablePlanner {
  return validateSaveablePlanner(data, 'save')
}

// ============================================================================
// Summary Schema
// ============================================================================

/**
 * Category schema for summary - accepts either MD or RR categories
 */
export const PlannerCategorySchema = z.union([MDCategorySchema, RRCategorySchema])

/**
 * Planner summary schema
 * Lightweight version for list display
 */
export const PlannerSummarySchema = z.object({
  /** Unique identifier */
  id: z.string(),
  /** Planner title */
  title: z.string(),
  /** Type of planner (MIRROR_DUNGEON, REFRACTED_RAILWAY) */
  plannerType: PlannerTypeSchema,
  /** Category (MD: 5F/10F/15F, RR: placeholder) */
  category: PlannerCategorySchema,
  /** Current save status */
  status: PlannerStatusSchema,
  /** Last modification timestamp for sorting */
  lastModifiedAt: z.string(),
  /** Explicit save timestamp (null if never saved) */
  savedAt: z.string().nullable(),
}).strict()

// ============================================================================
// Serialization Helpers
// ============================================================================

/**
 * Page state with Set types (used in components)
 */
interface PageStateWithSets {
  selectedKeywords: Set<string>
  selectedBuffIds: Set<number>
  selectedGiftIds: Set<string>
  observationGiftIds: Set<string>
  comprehensiveGiftIds: Set<string>
  floorSelections: {
    themePackId: string | null
    difficulty: 0 | 1 | 3
    giftIds: Set<string>
  }[]
}

/**
 * Serializable page state with arrays (for storage)
 */
interface SerializablePageState {
  selectedKeywords: string[]
  selectedBuffIds: number[]
  selectedGiftIds: string[]
  observationGiftIds: string[]
  comprehensiveGiftIds: string[]
  floorSelections: SerializableFloorSelection[]
}

/**
 * Convert page state Sets to arrays for storage
 *
 * @param state - Page state with Set types
 * @returns Serializable state with arrays
 *
 * @example
 * const serializable = serializeSets({
 *   selectedKeywords: new Set(['Combustion', 'Slash']),
 *   selectedBuffIds: new Set([1, 2, 3]),
 *   ...
 * })
 */
export function serializeSets(state: PageStateWithSets): SerializablePageState {
  return {
    selectedKeywords: Array.from(state.selectedKeywords),
    selectedBuffIds: Array.from(state.selectedBuffIds),
    selectedGiftIds: Array.from(state.selectedGiftIds),
    observationGiftIds: Array.from(state.observationGiftIds),
    comprehensiveGiftIds: Array.from(state.comprehensiveGiftIds),
    floorSelections: state.floorSelections.map((floor) => ({
      themePackId: floor.themePackId,
      difficulty: floor.difficulty,
      giftIds: Array.from(floor.giftIds),
    })),
  }
}

/**
 * Convert loaded arrays back to Sets for page state
 *
 * @param state - Serializable state with arrays (from storage)
 * @returns Page state with Set types
 *
 * @example
 * const pageState = deserializeSets({
 *   selectedKeywords: ['Combustion', 'Slash'],
 *   selectedBuffIds: [1, 2, 3],
 *   ...
 * })
 */
export function deserializeSets(state: SerializablePageState): PageStateWithSets {
  return {
    selectedKeywords: new Set(state.selectedKeywords),
    selectedBuffIds: new Set(state.selectedBuffIds),
    selectedGiftIds: new Set(state.selectedGiftIds),
    observationGiftIds: new Set(state.observationGiftIds),
    comprehensiveGiftIds: new Set(state.comprehensiveGiftIds),
    floorSelections: state.floorSelections.map((floor) => ({
      themePackId: floor.themePackId,
      difficulty: floor.difficulty,
      giftIds: new Set(floor.giftIds),
    })),
  }
}

// ============================================================================
// Server API Schemas
// ============================================================================

/**
 * Branded UUID schema for planner identifiers
 * Validates as UUID and provides branded type for PlannerId
 */
export const PlannerIdSchema = z.uuid().brand<'PlannerId'>()

/**
 * Server response schema for a single planner
 * Validates full planner data from the backend
 */
export const ServerPlannerResponseSchema = z.object({
  /** Unique identifier (UUID) */
  id: PlannerIdSchema,
  /** Planner title */
  title: z.string(),
  /** MD category */
  category: MDCategorySchema,
  /** Current save status */
  status: PlannerStatusSchema,
  /** Planner content as JSON string */
  content: z.string(),
  /** Schema version for data format migration support */
  schemaVersion: z.number().int().positive(),
  /** Game content version (e.g., 6 for MD6, 5 for RR5) */
  contentVersion: z.number().int().positive(),
  /** Type of planner (MIRROR_DUNGEON, REFRACTED_RAILWAY) */
  plannerType: PlannerTypeSchema,
  /** Server sync version for optimistic locking */
  syncVersion: z.number().int().positive(),
  /** Whether the planner is published to Gesellschaft */
  published: z.boolean(),
  /** Device identifier (nullable - server may return null) */
  deviceId: z.string().nullish(),
  /** ISO 8601 timestamp when planner was created */
  createdAt: z.string(),
  /** ISO 8601 timestamp when planner was last modified */
  lastModifiedAt: z.string(),
  /** ISO 8601 timestamp when planner was explicitly saved (nullable) */
  savedAt: z.string().nullish(),
  /** Number of upvotes (for published planners) */
  upvotes: z.number().int().nonnegative().optional(),
}).strict()

/**
 * Server summary schema for planner list display
 * Lightweight version without full content
 */
export const ServerPlannerSummarySchema = z.object({
  /** Unique identifier (UUID) */
  id: PlannerIdSchema,
  /** Planner title */
  title: z.string(),
  /** MD category */
  category: MDCategorySchema,
  /** Type of planner */
  plannerType: PlannerTypeSchema,
  /** Current save status */
  status: PlannerStatusSchema,
  /** Server sync version for optimistic locking */
  syncVersion: z.number().int().positive(),
  /** ISO 8601 timestamp when planner was last modified */
  lastModifiedAt: z.string(),
}).strict()

/**
 * Array schema for server planner summaries
 * Used for validating list endpoint responses
 */
export const ServerPlannerSummaryArraySchema = z.array(ServerPlannerSummarySchema)

/**
 * Response schema for bulk import operation
 */
export const ImportPlannersResponseSchema = z.object({
  /** Number of planners successfully imported */
  imported: z.number().int().nonnegative(),
  /** Total number of planners in request */
  total: z.number().int().nonnegative(),
  /** Summary of imported planners */
  planners: z.array(ServerPlannerSummarySchema),
}).strict()

/**
 * SSE event type for planner updates
 */
export const PlannerSseEventTypeSchema = z.enum(['created', 'updated', 'deleted'])

/**
 * Server-Sent Event schema for planner updates
 * Used for real-time sync notifications
 */
export const PlannerSseEventSchema = z.object({
  /** ID of the affected planner (UUID) */
  plannerId: PlannerIdSchema,
  /** Type of change that occurred */
  type: PlannerSseEventTypeSchema,
}).strict()

/**
 * Planner configuration schema from backend
 * Contains current versions for data format and game content
 */
/** Schema for MD version - validates against MD_VERSIONS constant */
const MDVersionSchema = z.union([z.literal(5), z.literal(6)])

export const PlannerConfigSchema = z.object({
  /** Current planner data schema version for migration support */
  schemaVersion: z.number().int().positive(),
  /** Current Mirror Dungeon version (e.g., 6 for MD6) */
  mdCurrentVersion: MDVersionSchema,
  /** Available Refracted Railway versions (e.g., [1, 5] for RR1 and RR5) */
  rrAvailableVersions: z.array(z.number().int().positive()).min(1),
}).strict()

/**
 * Planner config type
 */
export type PlannerConfig = z.infer<typeof PlannerConfigSchema>

// ============================================================================
// Export/Import Schemas
// ============================================================================

/**
 * Planner export item schema - light validation for individual planners
 * Allows any content structure for forward compatibility
 */
export const PlannerExportItemSchema = z.object({
  /** Planner ID (UUID) */
  id: z.string(),
  /** Planner metadata */
  metadata: PlannerMetadataSchema,
  /** Planner config (type and category) */
  config: PlannerConfigDiscriminatedSchema,
  /** Planner content - light validation, allow any structure */
  content: z.record(z.string(), z.unknown()),
}).strict()

/**
 * Export envelope schema for validating imported .danteplanner files
 * Light structural validation - does not reject based on version mismatch
 */
export const ExportEnvelopeSchema = z.object({
  /** Export format version for future migration support */
  exportVersion: z.number().int().positive(),
  /** ISO 8601 timestamp when export was created */
  exportedAt: z.string(),
  /** Device ID of the source device */
  sourceDeviceId: z.string(),
  /** Array of exported planners */
  planners: z.array(PlannerExportItemSchema),
}).strict()
