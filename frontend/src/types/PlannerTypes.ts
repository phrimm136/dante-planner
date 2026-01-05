import type { JSONContent } from '@tiptap/core'
import type { z } from 'zod'
import type { MDCategory, RRCategory, DungeonIdx, PlannerType } from '@/lib/constants'
import type { SinnerEquipment, SkillEAState } from './DeckTypes'

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
  themePackId: string | null
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
  /** User ID if authenticated (null for guest users) */
  userId: string | null
  /** Device identifier for local storage namespacing */
  deviceId: string
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
export type PlannerConfig = MDConfig | RRConfig

// ============================================================================
// Content Types
// ============================================================================

/**
 * Mirror Dungeon planner content - all state from PlannerMDNewPage
 * Note: category is in PlannerConfig, not here
 * All Set types are converted to arrays for JSON serialization
 */
export interface MDPlannerContent {
  /** Discriminator for runtime type narrowing */
  type: 'MIRROR_DUNGEON'
  /** Planner title */
  title: string
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
 */
export interface RRPlannerContent {
  /** Discriminator for runtime type narrowing */
  type: 'REFRACTED_RAILWAY'
  /** Planner title */
  title: string
  // Additional fields will be added when RR planner is implemented
}

/**
 * Union type for planner content
 * Content type matches config.type discriminator
 */
export type PlannerContent = MDPlannerContent | RRPlannerContent

/**
 * Complete saveable planner structure
 * Combines metadata, config (with category), and content for IndexedDB storage
 */
export interface SaveablePlanner {
  /** Planner metadata (id, status, timestamps, etc.) */
  metadata: PlannerMetadata
  /** Planner config (type discriminator and category) */
  config: PlannerConfig
  /** Planner content (all user-editable state) */
  content: PlannerContent
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
}

// ============================================================================
// Server API Types
// ============================================================================

/**
 * Branded type for planner UUID identifiers
 * Uses Zod's brand type for consistency with schema validation
 */
export type PlannerId = string & z.$brand<'PlannerId'>

/**
 * Server response for a single planner
 * Contains full planner data from the backend
 */
export interface ServerPlannerResponse {
  /** Unique identifier (UUID) */
  id: PlannerId
  /** User ID who owns this planner */
  userId: number
  /** Planner title */
  title: string
  /** Category (MD: 5F/10F/15F, RR: placeholder) - reuses existing constant types */
  category: MDCategory | RRCategory
  /** Current save status - reuses existing type */
  status: PlannerStatus
  /** Planner content as JSON string */
  content: string
  /** Schema version for data format migration support */
  schemaVersion: number
  /** Game content version (e.g., 6 for MD6, 5 for RR5) */
  contentVersion: number
  /** Type of planner (MIRROR_DUNGEON, REFRACTED_RAILWAY) */
  plannerType: PlannerType
  /** Server sync version for optimistic locking */
  syncVersion: number
  /** Device identifier (optional) */
  deviceId?: string
  /** ISO 8601 timestamp when planner was created */
  createdAt: string
  /** ISO 8601 timestamp when planner was last modified */
  lastModifiedAt: string
  /** ISO 8601 timestamp when planner was explicitly saved (optional) */
  savedAt?: string
}

/**
 * Server summary for planner list display
 * Lightweight version without full content
 */
export interface ServerPlannerSummary {
  /** Unique identifier (UUID) */
  id: PlannerId
  /** Planner title */
  title: string
  /** Category (MD: 5F/10F/15F, RR: placeholder) - reuses existing constant types */
  category: MDCategory | RRCategory
  /** Current save status - reuses existing type */
  status: PlannerStatus
  /** Server sync version for optimistic locking */
  syncVersion: number
  /** ISO 8601 timestamp when planner was last modified */
  lastModifiedAt: string
}

/**
 * Request payload for creating a new planner on the server
 */
export interface CreatePlannerRequest {
  /** MD category - required for new planners */
  category: MDCategory
  /** Planner title (optional, server may set default) */
  title?: string
  /** Initial save status (optional, defaults to 'draft') */
  status?: PlannerStatus
  /** Planner content as JSON string */
  content: string
  /** Device identifier for tracking (optional) */
  deviceId?: string
}

/**
 * Request payload for updating an existing planner
 */
export interface UpdatePlannerRequest {
  /** Updated title (optional) */
  title?: string
  /** Updated status (optional) */
  status?: PlannerStatus
  /** Updated content as JSON string (optional) */
  content?: string
  /** Current sync version for optimistic locking (required) */
  syncVersion: number
  /** Device identifier for tracking (optional) */
  deviceId?: string
}

/**
 * Request payload for bulk importing planners
 */
export interface ImportPlannersRequest {
  /** Array of planners to import */
  planners: CreatePlannerRequest[]
}

/**
 * Response from bulk import operation
 */
export interface ImportPlannersResponse {
  /** Number of planners successfully imported */
  imported: number
  /** Total number of planners in request */
  total: number
  /** Summary of imported planners */
  planners: ServerPlannerSummary[]
}

/**
 * Server-Sent Event for planner updates
 * Used for real-time sync notifications
 */
export interface PlannerSseEvent {
  /** ID of the affected planner (UUID) */
  plannerId: PlannerId
  /** Type of change that occurred */
  type: 'created' | 'updated' | 'deleted'
}

// ============================================================================
// Conflict Resolution Types
// ============================================================================

/**
 * Conflict state for save operations
 * Used when server returns 409 conflict
 */
export interface ConflictState {
  /** Server's current version */
  serverVersion: number
  /** ISO 8601 timestamp when conflict was detected */
  detectedAt: string
}

/**
 * User's choice for resolving a conflict
 * - 'overwrite': Force-save local version (sends syncVersion+1)
 * - 'discard': Reload from server (lose local changes)
 */
export type ConflictResolutionChoice = 'overwrite' | 'discard'
