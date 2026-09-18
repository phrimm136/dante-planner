import { serializeSets } from '../schemas/PlannerSchemas'

import type { EncodedGiftId, MDCategory } from '@/shared/gameData'
import type { NoteContent } from '@/shared/noteEditor'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { SinnerEquipment, SkillEAState } from '../types/DeckTypes'
import type {
  MDConfig,
  MDPlannerContent,
  MDSaveablePlanner,
  PlannerStatus,
} from '../types/PlannerTypes'

export interface PlannerState {
  title: string
  category: MDCategory
  selectedKeywords: Set<string>
  selectedBuffIds: Set<number>
  selectedGiftKeyword: string | null
  selectedGiftIds: Set<EncodedGiftId>
  observationGiftIds: Set<EncodedGiftId>
  comprehensiveGiftIds: Set<EncodedGiftId>
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
  skillEAState: Record<string, SkillEAState>
  floorSelections: FloorThemeSelection[]
  sectionNotes: Record<string, NoteContent>
}

export interface SaveablePlannerInput {
  state: PlannerState
  plannerId: string
  schemaVersion: number
  contentVersion: number
  plannerType: MDConfig['type']
  existingCreatedAt: string | null
  existingSyncVersion: number
  published: boolean
  status: PlannerStatus
}

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

export function createSaveablePlanner(input: SaveablePlannerInput): MDSaveablePlanner {
  const { state } = input
  const now = new Date().toISOString()

  const serialized = serializedSets(state)

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
    published: input.published,
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
