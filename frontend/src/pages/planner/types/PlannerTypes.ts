import type { JSONContent } from '@tiptap/core'
import type { z } from 'zod'
import type { MDCategory, RRCategory, DungeonIdx, PlannerType } from '@/shared/gameData'
import type {
  PlannerIdSchema,
  ServerPlannerResponseSchema,
  ServerPlannerSummarySchema,
  ImportPlannersResponseSchema,
} from '../schemas/PlannerSchemas'
import type { SinnerEquipment, SkillEAState } from './DeckTypes'
import type { ThemePackId } from '@/shared/gameData'

/**
 * Planner status for tracking save state
 * - 'draft': Unsaved or auto-saved locally
 * - 'saved': Explicitly saved by user (manual save)
 */
export type PlannerStatus = 'draft' | 'saved'

/**
 * Serializable version of FloorThemeSelection
 * Converts Set<string> to string[] for JSON serialization
 */
export interface SerializableFloorSelection {
  /** Selected theme pack ID, null if none selected */
  themePackId: ThemePackId | null
  /** Selected difficulty for this floor */
  difficulty: DungeonIdx
  /** Selected gift IDs as array (serialized from Set) */
  giftIds: string[]
}

/**
 * Serializable note content for storage
 * Wraps Tiptap's JSONContent
 */
export interface SerializableNoteContent {
  /** Rich text content as Tiptap JSONContent */
  content: JSONContent
}

/**
 * Planner metadata for tracking and identification
 */
export interface PlannerMetadata {
  /** Unique identifier (UUID v4) */
  id: string
  /** Planner title (identification, not game state) */
  title: string
  /** Current save status */
  status: PlannerStatus
  /** Schema version for data format migration support (1, 2, ...) */
  schemaVersion: number
  /** Game content version (e.g., 6 for MD6, 5 for RR5) */
  contentVersion: number
  /** Type of planner (MIRROR_DUNGEON, REFRACTED_RAILWAY) */
  plannerType: PlannerType
  /** Server sync version for optimistic locking (starts at 1) */
  syncVersion: number
  /** ISO 8601 timestamp when planner was first created */
  createdAt: string
  /** ISO 8601 timestamp when planner was last modified (auto-save or manual) */
  lastModifiedAt: string
  /** ISO 8601 timestamp when planner was explicitly saved (null for drafts never saved) */
  savedAt: string | null
  /** Device identifier for local storage namespacing */
  deviceId: string
  /** Whether planner is published (visible in community list) */
  published?: boolean | undefined
}

// ============================================================================
// Config Types (Discriminated Union)
// ============================================================================

/**
 * Mirror Dungeon config - discriminated by type field
 */
export interface MDConfig {
  /** Discriminator for type narrowing */
  type: 'MIRROR_DUNGEON'
  /** MD category (5F, 10F, 15F) */
  category: MDCategory
}

/**
 * Refracted Railway config - discriminated by type field
 */
export interface RRConfig {
  /** Discriminator for type narrowing */
  type: 'REFRACTED_RAILWAY'
  /** RR category (placeholder) */
  category: RRCategory
}

/**
 * Planner config union type
 * Use type narrowing: if (config.type === 'MIRROR_DUNGEON') { ... }
 */
export type PlannerEditorConfig = MDConfig | RRConfig

// ============================================================================
// Content Types
// ============================================================================

/**
 * Mirror Dungeon planner content - all state from PlannerMDNewPage
 * Note: title is in PlannerMetadata, category is in PlannerEditorConfig
 * All Set types are converted to arrays for JSON serialization
 */
export interface MDPlannerContent {
  /** Selected planner keywords (serialized from Set) */
  selectedKeywords: string[]
  /** Selected start buff IDs (serialized from Set<number>) */
  selectedBuffIds: number[]
  /** Currently selected gift keyword filter (null if none) */
  selectedGiftKeyword: string | null
  /** Selected start gift IDs (serialized from Set) */
  selectedGiftIds: string[]
  /** Observation gift IDs (serialized from Set) */
  observationGiftIds: string[]
  /** Comprehensive gift IDs with enhancement encoding (serialized from Set) */
  comprehensiveGiftIds: string[]
  /** Equipment configuration per sinner */
  equipment: Record<string, SinnerEquipment>
  /** Deployment order as array of sinner indices */
  deploymentOrder: number[]
  /** Skill EA state per sinner */
  skillEAState: Record<string, SkillEAState>
  /** Floor theme selections (15 floors max, serialized) */
  floorSelections: SerializableFloorSelection[]
  /** Section notes keyed by section identifier */
  sectionNotes: Record<string, SerializableNoteContent>
}

/**
 * Refracted Railway planner content - placeholder for future implementation
 * Note: title is in PlannerMetadata
 */
export interface RRPlannerContent {
  // Fields will be added when RR planner is implemented
}

/**
 * Union type for planner content
 * Content type matches config.type discriminator
 */
export type PlannerContent = MDPlannerContent | RRPlannerContent

/**
 * Mirror Dungeon planner: config and content are pinned to the MD half
 */
export interface MDSaveablePlanner {
  /** Planner metadata (id, status, timestamps, etc.) */
  metadata: PlannerMetadata
  /** MD config (type discriminator and MD category) */
  config: MDConfig
  /** MD content (all user-editable state) */
  content: MDPlannerContent
}

/**
 * Refracted Railway planner: config and content are pinned to the RR half
 */
export interface RRSaveablePlanner {
  /** Planner metadata (id, status, timestamps, etc.) */
  metadata: PlannerMetadata
  /** RR config (type discriminator and RR category) */
  config: RRConfig
  /** RR content (all user-editable state) */
  content: RRPlannerContent
}

/**
 * Complete saveable planner structure
 *
 * Discriminated at the root, so `content` follows `config`. Select a branch
 * with `isMDPlanner` rather than testing `config.type` inline.
 */
export type SaveablePlanner = MDSaveablePlanner | RRSaveablePlanner

/**
 * Narrow a planner to its Mirror Dungeon branch.
 *
 * A bare `planner.config.type === 'MIRROR_DUNGEON'` narrows `planner.config`
 * and stops there — the discriminant sits one level below the union root, so
 * the sibling `content` stays widened. This predicate carries it across.
 */
export function isMDPlanner(planner: SaveablePlanner): planner is MDSaveablePlanner {
  return planner.config.type === 'MIRROR_DUNGEON'
}

/**
 * Lightweight planner summary for list display
 * Used in planner list views to avoid loading full content
 */
export interface PlannerSummary {
  /** Unique identifier */
  id: string
  /** Planner title */
  title: string
  /** Type of planner (MIRROR_DUNGEON, REFRACTED_RAILWAY) */
  plannerType: PlannerType
  /** Category (MD: 5F/10F/15F, RR: placeholder) */
  category: MDCategory | RRCategory
  /** Current save status */
  status: PlannerStatus
  /** Last modification timestamp for sorting */
  lastModifiedAt: string
  /** Explicit save timestamp (null if never saved) */
  savedAt: string | null
  /** Whether planner is published (visible in community list) */
  published?: boolean
  /** Server sync version for comparing local vs server state */
  syncVersion?: number
  /** Selected keywords for display (MD planners only) */
  selectedKeywords?: string[]
  /** Server tombstone; present only on rows a sync listing carries as deleted */
  deletedAt?: string
}

// ============================================================================
// Server API Types
// ============================================================================

/** Branded type for planner UUID identifiers */
export type PlannerId = z.infer<typeof PlannerIdSchema>

/** Server response for a single planner (full data from the backend) */
export type ServerPlannerResponse = z.infer<typeof ServerPlannerResponseSchema>

/** Server summary for planner list display (no content) */
export type ServerPlannerSummary = z.infer<typeof ServerPlannerSummarySchema>

/** What the server confirms about a write: which version it assigned. */
export interface ServerAck {
  syncVersion: number
}

/**
 * Request payload for creating a new planner on the server
 */
export interface UpsertPlannerRequest {
  /** Client-generated planner ID (UUID) */
  id: string
  /** MD category - required for new planners */
  category: MDCategory
  /** Planner title (optional, server may set default) */
  title?: string
  /** Initial save status (optional, defaults to 'draft') */
  status?: PlannerStatus
  /** Planner content as JSON string */
  content: string
  /** Game content version (e.g., 6 for MD6, 5 for RR5) */
  contentVersion: number
  /** Type of planner (MIRROR_DUNGEON, REFRACTED_RAILWAY) */
  plannerType: PlannerType
  /** Device identifier for tracking (optional) */
  deviceId?: string
  /** Sync version for optimistic locking (upsert only, optional) */
  syncVersion?: number
  /** Selected keywords for display in list view (MD planners only) */
  selectedKeywords?: string[]
}

/**
 * Request payload for bulk importing planners
 */
export interface ImportPlannersRequest {
  /** Array of planners to import */
  planners: UpsertPlannerRequest[]
}

/** Response from bulk import operation */
export type ImportPlannersResponse = z.infer<typeof ImportPlannersResponseSchema>

// ============================================================================
// Conflict Resolution Types
// ============================================================================

/**
 * Conflict state for save operations
 * Used when server returns 409 conflict
 */
export interface ConflictState {
  /** Server's current version, null when the conflict did not report one */
  serverVersion: number | null
  /** ISO 8601 timestamp when conflict was detected */
  detectedAt: string
}

/**
 * User's choice for resolving a conflict
 * - 'overwrite': Force-save local version (sends syncVersion+1)
 * - 'discard': Reload from server (lose local changes)
 * - 'both': Keep both versions - create copy of server with new UUID + "(Copy)" suffix
 */
export type ConflictResolutionChoice = 'overwrite' | 'discard' | 'both'

// ============================================================================
// Export/Import Types
// ============================================================================

/**
 * Single planner item in export file
 * Essentially SaveablePlanner with id exposed at top level for clarity
 * Device-agnostic: deviceId is stripped from metadata on export
 */
export interface PlannerExportItem {
  /** Planner ID (matches metadata.id) */
  id: string
  /** Planner metadata (timestamps, status, etc.) */
  metadata: PlannerMetadata
  /** Planner config (type discriminator and category) */
  config: PlannerEditorConfig
  /** Planner content (all user-editable state) */
  content: PlannerContent
}

/**
 * Export file envelope structure
 * Wraps planner array with export metadata for versioning and tracking
 */
export interface ExportEnvelope {
  /** Export format version for future migration support */
  exportVersion: number
  /** ISO 8601 timestamp when export was created */
  exportedAt: string
  /** Device ID of the source device (informational) */
  sourceDeviceId: string
  /** Array of exported planners */
  planners: PlannerExportItem[]
}
