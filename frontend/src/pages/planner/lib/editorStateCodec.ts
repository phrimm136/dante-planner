import {
  SINNERS,
  MAX_LEVEL,
  DEFAULT_SKILL_EA,
  DUNGEON_IDX,
  migrateKeywords,
} from '@/shared/gameData'
import { createEmptyNoteContent } from '@/shared/noteEditor'
import egoSpecList from '@static/data/egoSpecList.json'

import { createEmptyFilterSets } from '../types/DeckTypes'

import type { MDCategory, DungeonIdx } from '@/shared/gameData'
import type { NoteContent } from '@/shared/noteEditor'
import type { FloorThemeSelection } from '@/pages/themePack'
import type {
  SinnerEquipment,
  SkillEAState,
  DeckFilterState,
  ThreadspinTier,
} from '../types/DeckTypes'
import type { MDPlannerContent } from '../types/PlannerTypes'
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

// ============================================================================
// Default State Factories
// ============================================================================

/**
 * Creates default equipment for all 12 sinners
 * Each sinner gets their base identity (uptie 4, max level) and ZAYIN EGO
 */
export function createDefaultEquipment(): Record<string, SinnerEquipment> {
  const equipment: Record<string, SinnerEquipment> = {}
  SINNERS.forEach((_, index) => {
    const sinnerCode = String(index + 1)
    const sinnerIdPart = sinnerCode.padStart(2, '0')
    const defaultIdentityId = `1${sinnerIdPart}01`
    const defaultEgoId = `2${sinnerIdPart}01`
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
 * Creates default skill EA state for all 12 sinners
 * Each sinner gets default EA values: S1=3, S2=2, S3=1
 */
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

/**
 * Creates default floor selections for 15 floors
 * All floors start with no theme pack selected and normal difficulty
 */
export function createDefaultFloorSelections(): FloorThemeSelection[] {
  return Array.from({ length: 15 }, () => ({
    themePackId: null,
    difficulty: DUNGEON_IDX.NORMAL as DungeonIdx,
    giftIds: new Set<string>(),
  }))
}

/**
 * Creates default section notes for all planner sections
 * Includes 6 fixed sections + 15 floor sections
 */
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

/**
 * Creates default deck filter state
 */
export function createDefaultDeckFilterState(): DeckFilterState {
  return {
    entityMode: 'identity',
    ...createEmptyFilterSets(),
    searchQuery: '',
  }
}

// ============================================================================
// Codec
// ============================================================================

/** Planner identity fields that travel beside the content, not inside it. */
export interface EditorMetadata {
  title: string
  category: MDCategory
  isPublished: boolean
}

/** Editor fields a stored planner determines, in their in-memory (Set-bearing) form. */
export interface HydratedEditorState extends EditorMetadata {
  equipment: Record<string, SinnerEquipment>
  floorSelections: FloorThemeSelection[]
  comprehensiveGiftIds: Set<string>
  deploymentOrder: number[]
  selectedKeywords: Set<string>
  selectedBuffIds: Set<number>
  selectedGiftIds: Set<string>
  observationGiftIds: Set<string>
  selectedGiftKeyword: string | null
  skillEAState: Record<string, SkillEAState>
  deckFilterState: DeckFilterState
  sectionNotes: Record<string, NoteContent>
}

/** Fields the projection reads; editor state is a superset of it. */
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

/**
 * Read a stored planner into editor state.
 *
 * Content that reaches this point has survived IndexedDB or a server round trip
 * across schema versions, so every array field is re-checked rather than
 * trusted: a missing or non-array value falls back to the default for that
 * field. Keyword ids are migrated through their renames, section notes are
 * merged over the full default key set so plans written before a section
 * existed still get an entry for it, and the deck filter — which no stored
 * field feeds — is reset.
 */
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
      ? content.floorSelections.map((floor) => ({
          themePackId: floor?.themePackId ?? null,
          difficulty: floor?.difficulty ?? (DUNGEON_IDX.NORMAL as DungeonIdx),
          giftIds: new Set(Array.isArray(floor?.giftIds) ? floor.giftIds : []),
        }))
      : createDefaultFloorSelections(),
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

/** Project editor state onto the save-facing planner state, dropping editor-only fields. */
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
