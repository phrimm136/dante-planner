import type { PlannerContentState } from '../plannerState'

export const PLANNER_CONTENT_KEYS = [
  'title',
  'category',
  'isPublished',
  'equipment',
  'floorSelections',
  'comprehensiveGiftIds',
  'deploymentOrder',
  'selectedKeywords',
  'selectedBuffIds',
  'selectedGiftIds',
  'observationGiftIds',
  'selectedGiftKeyword',
  'skillEAState',
  'sectionNotes',
] as const satisfies ReadonlyArray<keyof PlannerContentState>
