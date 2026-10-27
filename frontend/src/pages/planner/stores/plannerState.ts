import type { EncodedGiftId, MDCategory } from '@/shared/gameData'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { NoteContent } from '@/shared/noteEditor'
import type { DeckFilterState, SinnerEquipment, SkillEAState } from '../types/DeckTypes'

export interface PlannerContentState {
  title: string
  category: MDCategory
  isPublished: boolean
  equipment: Record<string, SinnerEquipment>
  floorSelections: FloorThemeSelection[]
  comprehensiveGiftIds: Set<EncodedGiftId>
  deploymentOrder: number[]
  selectedKeywords: Set<string>
  selectedBuffIds: Set<number>
  selectedGiftIds: Set<EncodedGiftId>
  observationGiftIds: Set<EncodedGiftId>
  selectedGiftKeyword: string | null
  skillEAState: Record<string, SkillEAState>
  sectionNotes: Record<string, NoteContent>
}

export interface PlannerSessionState {
  contentVersion: number
  identityMaxLevel: number
  deckFilterState: DeckFilterState
  deckVisibleCount: number
  visibleSections: number
}

export type PlannerStoreState = PlannerContentState & PlannerSessionState
