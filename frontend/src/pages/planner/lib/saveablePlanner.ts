import { serializeSets } from '../schemas/PlannerSchemas'

import type { MDCategory } from '@/shared/gameData'
import type { NoteContent } from '@/shared/noteEditor'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { SinnerEquipment, SkillEAState } from '../types/DeckTypes'
import type {
  MDConfig,
  MDPlannerContent,
  MDSaveablePlanner,
  PlannerStatus,
} from '../types/PlannerTypes'

/**
 * Planner state interface matching PlannerMDNewPage state structure
 * Uses Set types for in-memory representation
 */
export interface PlannerState {
  /** Planner title */
  title: string
  /** MD category (5F, 10F, 15F) */
  category: MDCategory
  /** Selected planner keywords */
  selectedKeywords: Set<string>
  /** Selected start buff IDs */
  selectedBuffIds: Set<number>
  /** Currently selected gift keyword filter */
  selectedGiftKeyword: string | null
  /** Selected start gift IDs */
  selectedGiftIds: Set<string>
  /** Observation gift IDs */
  observationGiftIds: Set<string>
  /** Comprehensive gift IDs with enhancement encoding */
  comprehensiveGiftIds: Set<string>
  /** Equipment configuration per sinner */
  equipment: Record<string, SinnerEquipment>
  /** Deployment order as array of sinner indices */
  deploymentOrder: number[]
  /** Skill EA state per sinner */
  skillEAState: Record<string, SkillEAState>
  /** Floor theme selections (has Set inside) */
  floorSelections: FloorThemeSelection[]
  /** Section notes keyed by section identifier */
  sectionNotes: Record<string, NoteContent>
}

/** Everything a `SaveablePlanner` needs that is not derived from the editor state. */
export interface SaveablePlannerInput {
  state: PlannerState
  plannerId: string
  deviceId: string
  schemaVersion: number
  contentVersion: number
  /** The editor state carries an MD category, so only the MD branch is buildable. */
  plannerType: MDConfig['type']
  /** Original creation timestamp, or null for a planner being created now. */
  existingCreatedAt: string | null
  existingSyncVersion: number
  published: boolean
  status: PlannerStatus
}

/** The Set-valued half of the editor state, as arrays. */
function serializedSets(state: PlannerState) {
  return serializeSets({
    selectedKeywords: state.selectedKeywords,
    selectedBuffIds: state.selectedBuffIds,
    selectedGiftIds: state.selectedGiftIds,
    observationGiftIds: state.observationGiftIds,
    comprehensiveGiftIds: state.comprehensiveGiftIds,
    floorSelections: state.floorSelections,
  })
}

/**
 * Serialize PlannerState to SaveablePlanner format
 */
export function createSaveablePlanner(input: SaveablePlannerInput): MDSaveablePlanner {
  const { state } = input
  const now = new Date().toISOString()

  const serialized = serializedSets(state)

  // Convert NoteContent to SerializableNoteContent
  const serializableNotes: Record<
    string,
    { content: (typeof state.sectionNotes)[string]['content'] }
  > = {}
  for (const [key, note] of Object.entries(state.sectionNotes)) {
    serializableNotes[key] = { content: note.content }
  }

  const metadata = {
    id: input.plannerId,
    title: state.title,
    status: input.status,
    schemaVersion: input.schemaVersion,
    contentVersion: input.contentVersion,
    plannerType: input.plannerType,
    syncVersion: input.existingSyncVersion,
    createdAt: input.existingCreatedAt ?? now,
    lastModifiedAt: now,
    savedAt: input.status === 'saved' ? now : null,
    published: input.published,
    deviceId: input.deviceId,
  }

  const content: MDPlannerContent = {
    selectedKeywords: serialized.selectedKeywords,
    selectedBuffIds: serialized.selectedBuffIds,
    selectedGiftKeyword: state.selectedGiftKeyword,
    selectedGiftIds: serialized.selectedGiftIds,
    observationGiftIds: serialized.observationGiftIds,
    comprehensiveGiftIds: serialized.comprehensiveGiftIds,
    equipment: state.equipment,
    deploymentOrder: state.deploymentOrder,
    skillEAState: state.skillEAState,
    floorSelections: serialized.floorSelections,
    sectionNotes: serializableNotes,
  }

  return { metadata, config: { type: input.plannerType, category: state.category }, content }
}

/**
 * Deep comparison for dirty state detection
 */
export function stateToComparableString(state: PlannerState): string {
  return JSON.stringify({
    title: state.title,
    category: state.category,
    selectedGiftKeyword: state.selectedGiftKeyword,
    equipment: state.equipment,
    deploymentOrder: state.deploymentOrder,
    skillEAState: state.skillEAState,
    sectionNotes: state.sectionNotes,
    ...serializedSets(state),
  })
}
