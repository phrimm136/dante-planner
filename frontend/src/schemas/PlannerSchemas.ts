import { z } from 'zod'
import { DUNGEON_IDX, MAX_LEVEL, MD_CATEGORIES } from '@/lib/constants'
import { JSONContentSchema } from './NoteEditorSchemas'
import type { SerializableFloorSelection } from '@/types/PlannerTypes'

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
const EGOSlotsSchema = z.record(z.string(), EquippedEGOSchema)

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
// Floor Selection Schemas
// ============================================================================

/**
 * Serializable floor selection schema
 * Used for floor theme/gift selections per floor
 */
export const SerializableFloorSelectionSchema = z.object({
  /** Selected theme pack ID, null if none selected */
  themePackId: z.string().nullable(),
  /** Selected difficulty for this floor */
  difficulty: DungeonIdxSchema,
  /** Selected gift IDs as array (serialized from Set) - validated as gift IDs */
  giftIds: z.array(GiftIdSchema),
}).strict()

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
 * Planner metadata schema
 * Contains tracking and identification data
 */
export const PlannerMetadataSchema = z.object({
  /** Unique identifier (UUID v4) */
  id: z.string().uuid(),
  /** Current save status */
  status: PlannerStatusSchema,
  /** Schema version for migration support */
  version: z.number().int().positive(),
  /** Server sync version for optimistic locking (starts at 1) */
  syncVersion: z.number().int().positive().default(1),
  /** ISO 8601 timestamp when planner was first created */
  createdAt: z.string().datetime(),
  /** ISO 8601 timestamp when planner was last modified */
  lastModifiedAt: z.string().datetime(),
  /** ISO 8601 timestamp when planner was explicitly saved */
  savedAt: z.string().datetime().nullable(),
  /** User ID if authenticated */
  userId: z.string().nullable(),
  /** Device identifier for local storage namespacing */
  deviceId: z.string(),
}).strict()

/**
 * Planner content schema
 * All user-editable state from PlannerMDNewPage
 */
export const PlannerContentSchema = z.object({
  /** Planner title */
  title: z.string(),
  /** MD category (5F, 10F, 15F) */
  category: MDCategorySchema,
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
  /** Floor theme selections (15 floors max, serialized) */
  floorSelections: z.array(SerializableFloorSelectionSchema),
  /** Section notes keyed by section identifier */
  sectionNotes: z.record(z.string(), SerializableNoteContentSchema),
}).strict()

// ============================================================================
// Complete Planner Schemas
// ============================================================================

/**
 * Saveable planner schema
 * Complete structure for IndexedDB storage
 */
export const SaveablePlannerSchema = z.object({
  /** Planner metadata (id, status, timestamps, etc.) */
  metadata: PlannerMetadataSchema,
  /** Planner content (all user-editable state) */
  content: PlannerContentSchema,
}).strict()

/**
 * Planner summary schema
 * Lightweight version for list display
 */
export const PlannerSummarySchema = z.object({
  /** Unique identifier */
  id: z.string(),
  /** Planner title */
  title: z.string(),
  /** MD category */
  category: MDCategorySchema,
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
  floorSelections: Array<{
    themePackId: string | null
    difficulty: 0 | 1 | 3
    giftIds: Set<string>
  }>
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
 * Validates as UUID and provides branded type
 */
export const PlannerIdSchema = z.string().uuid()

/**
 * Server response schema for a single planner
 * Validates full planner data from the backend
 */
export const ServerPlannerResponseSchema = z.object({
  /** Unique identifier (UUID) */
  id: PlannerIdSchema,
  /** User ID who owns this planner */
  userId: z.number().int().positive(),
  /** Planner title */
  title: z.string(),
  /** MD category */
  category: MDCategorySchema,
  /** Current save status */
  status: PlannerStatusSchema,
  /** Planner content as JSON string */
  content: z.string(),
  /** Schema version for migration support */
  version: z.number().int().positive(),
  /** Server sync version for optimistic locking */
  syncVersion: z.number().int().positive(),
  /** Device identifier (optional) */
  deviceId: z.string().optional(),
  /** ISO 8601 timestamp when planner was created */
  createdAt: z.string(),
  /** ISO 8601 timestamp when planner was last modified */
  lastModifiedAt: z.string(),
  /** ISO 8601 timestamp when planner was explicitly saved (optional) */
  savedAt: z.string().optional(),
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
