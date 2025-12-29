import type { JSONContent } from '@tiptap/core'
import type { MDCategory, DungeonIdx } from '@/lib/constants'
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
  /** Schema version for migration support */
  version: number
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

/**
 * Serialized planner content - all state from PlannerMDNewPage
 * All Set types are converted to arrays for JSON serialization
 */
export interface PlannerContent {
  /** Planner title */
  title: string
  /** MD category (5F, 10F, 15F) */
  category: MDCategory
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
 * Complete saveable planner structure
 * Combines metadata and content for IndexedDB storage
 */
export interface SaveablePlanner {
  /** Planner metadata (id, status, timestamps, etc.) */
  metadata: PlannerMetadata
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
  /** MD category */
  category: MDCategory
  /** Current save status */
  status: PlannerStatus
  /** Last modification timestamp for sorting */
  lastModifiedAt: string
  /** Explicit save timestamp (null if never saved) */
  savedAt: string | null
}
