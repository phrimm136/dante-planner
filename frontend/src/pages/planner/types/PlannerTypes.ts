import type { JSONContent } from '@tiptap/core'
import type { z } from 'zod'
import type {
  MDCategory,
  RRCategory,
  DungeonIdx,
  EncodedGiftId,
  PlannerType,
} from '@/shared/gameData'
import type {
  PlannerIdSchema,
  ServerPlannerResponseSchema,
  ServerPlannerSummarySchema,
} from '../schemas/PlannerSchemas'
import type { SinnerEquipment, SkillEAState } from './DeckTypes'
import type { ThemePackId } from '@/shared/gameData'

export type PlannerStatus = 'draft' | 'saved'

export interface SerializableFloorSelection {
  themePackId: ThemePackId | null
  difficulty: DungeonIdx
  giftIds: EncodedGiftId[]
}

export interface SerializableNoteContent {
  content: JSONContent
}

export interface PlannerMetadata {
  id: string
  title: string
  status: PlannerStatus
  schemaVersion: number
  contentVersion: number
  plannerType: PlannerType
  syncVersion: number
  createdAt: string
  lastModifiedAt: string
  published?: boolean | undefined
}

export interface MDConfig {
  type: 'MIRROR_DUNGEON'
  category: MDCategory
}

export interface RRConfig {
  type: 'REFRACTED_RAILWAY'
  category: RRCategory
}

export type PlannerEditorConfig = MDConfig | RRConfig

export interface MDPlannerContent {
  selectedKeywords: string[]
  selectedBuffIds: number[]
  selectedGiftKeyword: string | null
  selectedGiftIds: EncodedGiftId[]
  observationGiftIds: EncodedGiftId[]
  comprehensiveGiftIds: EncodedGiftId[]
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
  skillEAState: Record<string, SkillEAState>
  floorSelections: SerializableFloorSelection[]
  sectionNotes: Record<string, SerializableNoteContent>
}

export interface RRPlannerContent {
  // Fields will be added when RR planner is implemented
}

export type PlannerContent = MDPlannerContent | RRPlannerContent

export interface MDSaveablePlanner {
  metadata: PlannerMetadata
  config: MDConfig
  content: MDPlannerContent
}

export interface RRSaveablePlanner {
  metadata: PlannerMetadata
  config: RRConfig
  content: RRPlannerContent
}

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

export interface PlannerSummary {
  id: string
  title: string
  plannerType: PlannerType
  category: MDCategory | RRCategory
  status: PlannerStatus
  lastModifiedAt: string
  published?: boolean
  syncVersion?: number
  selectedKeywords?: string[]
  deletedAt?: string
}

export interface LocalTombstone {
  id: string
  syncVersion: number
  deletedAt: string
}

export type PlannerId = z.infer<typeof PlannerIdSchema>

export type ServerPlannerResponse = z.infer<typeof ServerPlannerResponseSchema>

export type ServerPlannerSummary = z.infer<typeof ServerPlannerSummarySchema>

export interface ServerAck {
  syncVersion: number
}

export interface UpsertPlannerRequest {
  id: string
  category: MDCategory
  title?: string
  status?: PlannerStatus
  content: string
  contentVersion: number
  plannerType: PlannerType
  syncVersion?: number
  selectedKeywords?: string[]
}

export interface ConflictState {
  serverVersion: number | null
  detectedAt: string
}

export type ConflictResolutionChoice = 'overwrite' | 'discard' | 'both'

export interface PlannerExportItem {
  id: string
  metadata: PlannerMetadata
  config: PlannerEditorConfig
  content: PlannerContent
}

export interface ExportEnvelope {
  exportVersion: number
  exportedAt: string
  planners: PlannerExportItem[]
}
