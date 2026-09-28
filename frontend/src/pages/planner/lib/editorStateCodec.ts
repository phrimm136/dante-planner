import {
  SINNERS,
  MAX_LEVEL,
  DEFAULT_SKILL_EA,
  DUNGEON_IDX,
  migrateKeywords,
  IdentityIdSchema,
  EGOIdSchema,
  allowedDifficulties,
} from '@/shared/gameData'
import { createEmptyNoteContent } from '@/shared/noteEditor'
import egoSpecList from '@static/data/egoSpecList.json'

import { createEmptyFilterSets } from '../types/DeckTypes'

import type { MDCategory, DungeonIdx, EncodedGiftId } from '@/shared/gameData'
import type { NoteContent } from '@/shared/noteEditor'
import type { FloorThemeSelection } from '@/pages/themePack'
import type {
  SinnerEquipment,
  SkillEAState,
  DeckFilterState,
  ThreadspinTier,
} from '../types/DeckTypes'
import type { MDPlannerContent, SerializableFloorSelection } from '../types/PlannerTypes'
import type { PlannerState } from './saveablePlanner'

const DEFAULT_ZAYIN_MAX_THREADSPIN: Record<string, ThreadspinTier> = (() => {
  const lookup = egoSpecList as Record<string, { maxThreadspin: 4 | 5 }>
  const out: Record<string, ThreadspinTier> = {}
  SINNERS.forEach((_, index) => {
    const id = `2${String(index + 1).padStart(2, '0')}01`
    out[id] = lookup[id]?.maxThreadspin ?? 4
  })
  return out
})()

export function createDefaultEquipment(): Record<string, SinnerEquipment> {
  const equipment: Record<string, SinnerEquipment> = {}
  SINNERS.forEach((_, index) => {
    const sinnerCode = String(index + 1)
    const sinnerIdPart = sinnerCode.padStart(2, '0')
    const defaultIdentityId = IdentityIdSchema.parse(`1${sinnerIdPart}01`)
    const defaultEgoId = EGOIdSchema.parse(`2${sinnerIdPart}01`)
    equipment[sinnerCode] = {
      identity: { id: defaultIdentityId, uptie: 4, level: MAX_LEVEL },
      egos: {
        ZAYIN: { id: defaultEgoId, threadspin: DEFAULT_ZAYIN_MAX_THREADSPIN[defaultEgoId] ?? 4 },
      },
    }
  })
  return equipment
}

/**
 * Rebuild the skill-EA record from whatever survived storage.
 *
 * No production path enforced the value type — the enforcing schema is
 * test-only — so a slot could hold the string "3", and `total += "3"` turns a
 * running total into "0321" rather than a number.
 */
function reconcileSkillEAState(value: unknown): Record<string, SkillEAState> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return createDefaultSkillEAState()
  }

  const reconciled: Record<string, SkillEAState> = {}
  for (const [sinnerKey, slots] of Object.entries(value)) {
    if (typeof slots !== 'object' || slots === null || Array.isArray(slots)) continue

    const numericSlots: Record<string, number> = {}
    for (const [slotKey, ea] of Object.entries(slots as Record<string, unknown>)) {
      const numeric = typeof ea === 'string' ? Number(ea) : ea
      if (typeof numeric === 'number' && Number.isFinite(numeric)) {
        numericSlots[slotKey] = numeric
      }
    }
    reconciled[sinnerKey] = numericSlots as SkillEAState
  }

  return Object.keys(reconciled).length === 0 ? createDefaultSkillEAState() : reconciled
}

export function createDefaultSkillEAState(): Record<string, SkillEAState> {
  const state: Record<string, SkillEAState> = {}
  SINNERS.forEach((_, index) => {
    state[String(index + 1)] = { ...DEFAULT_SKILL_EA }
  })
  return state
}

export function defaultFloorDifficulty(category: MDCategory, floorIndex: number): DungeonIdx {
  return allowedDifficulties(category, floorIndex)?.[0] ?? DUNGEON_IDX.NORMAL
}

export function toFloorThemeSelection(
  floor: SerializableFloorSelection | undefined,
  category: MDCategory,
  floorIndex: number,
): FloorThemeSelection {
  return {
    themePackId: floor?.themePackId || null,
    difficulty: floor?.difficulty ?? defaultFloorDifficulty(category, floorIndex),
    giftIds: new Set(Array.isArray(floor?.giftIds) ? floor.giftIds : []),
  }
}

export function createDefaultFloorSelections(category: MDCategory): FloorThemeSelection[] {
  return Array.from({ length: 15 }, (_, floorIndex) => ({
    themePackId: null,
    difficulty: defaultFloorDifficulty(category, floorIndex),
    giftIds: new Set<EncodedGiftId>(),
  }))
}

export function createDefaultSectionNotes(): Record<string, NoteContent> {
  const notes: Record<string, NoteContent> = {
    intro: createEmptyNoteContent(),
    deckBuilder: createEmptyNoteContent(),
    startBuffs: createEmptyNoteContent(),
    startGifts: createEmptyNoteContent(),
    observation: createEmptyNoteContent(),
    skillReplacement: createEmptyNoteContent(),
    comprehensiveGifts: createEmptyNoteContent(),
    outro: createEmptyNoteContent(),
  }
  for (let i = 0; i < 15; i++) {
    notes[`floor-${i}`] = createEmptyNoteContent()
  }
  return notes
}

export function createDefaultDeckFilterState(): DeckFilterState {
  return {
    entityMode: 'identity',
    ...createEmptyFilterSets(),
    searchQuery: '',
  }
}

export interface EditorMetadata {
  title: string
  category: MDCategory
  isPublished: boolean
}

export interface HydratedEditorState extends EditorMetadata {
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
  deckFilterState: DeckFilterState
  sectionNotes: Record<string, NoteContent>
}

export type ProjectableEditorState = Pick<
  HydratedEditorState,
  | 'title'
  | 'category'
  | 'selectedKeywords'
  | 'selectedBuffIds'
  | 'selectedGiftKeyword'
  | 'selectedGiftIds'
  | 'observationGiftIds'
  | 'comprehensiveGiftIds'
  | 'equipment'
  | 'deploymentOrder'
  | 'skillEAState'
  | 'floorSelections'
  | 'sectionNotes'
>

export function hydrateEditorState(
  content: MDPlannerContent,
  metadata: EditorMetadata,
): HydratedEditorState {
  return {
    title: metadata.title,
    category: metadata.category,
    isPublished: metadata.isPublished,

    equipment: content.equipment ?? createDefaultEquipment(),
    floorSelections: Array.isArray(content.floorSelections)
      ? content.floorSelections.map((floor, floorIndex) =>
          toFloorThemeSelection(floor, metadata.category, floorIndex),
        )
      : createDefaultFloorSelections(metadata.category),
    comprehensiveGiftIds: new Set(
      Array.isArray(content.comprehensiveGiftIds) ? content.comprehensiveGiftIds : [],
    ),
    deploymentOrder: Array.isArray(content.deploymentOrder) ? content.deploymentOrder : [],

    selectedKeywords: new Set(migrateKeywords(content.selectedKeywords)),
    selectedBuffIds: new Set(Array.isArray(content.selectedBuffIds) ? content.selectedBuffIds : []),
    selectedGiftIds: new Set(Array.isArray(content.selectedGiftIds) ? content.selectedGiftIds : []),
    observationGiftIds: new Set(
      Array.isArray(content.observationGiftIds) ? content.observationGiftIds : [],
    ),
    selectedGiftKeyword: content.selectedGiftKeyword ?? null,
    skillEAState: reconcileSkillEAState(content.skillEAState),
    deckFilterState: createDefaultDeckFilterState(),

    sectionNotes: {
      ...createDefaultSectionNotes(),
      ...(content.sectionNotes
        ? Object.fromEntries(
            Object.entries(content.sectionNotes).map(([key, note]) => [
              key,
              { content: note?.content ?? '' },
            ]),
          )
        : {}),
    },
  }
}

export function projectEditorState(state: ProjectableEditorState): PlannerState {
  return {
    title: state.title,
    category: state.category,
    selectedKeywords: state.selectedKeywords,
    selectedBuffIds: state.selectedBuffIds,
    selectedGiftKeyword: state.selectedGiftKeyword,
    selectedGiftIds: state.selectedGiftIds,
    observationGiftIds: state.observationGiftIds,
    comprehensiveGiftIds: state.comprehensiveGiftIds,
    equipment: state.equipment,
    deploymentOrder: state.deploymentOrder,
    skillEAState: state.skillEAState,
    floorSelections: state.floorSelections,
    sectionNotes: state.sectionNotes,
  }
}
