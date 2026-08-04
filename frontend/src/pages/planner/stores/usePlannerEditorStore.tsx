import { createContext, useContext, useState } from 'react'
import { createStore, useStore } from 'zustand'
import { devtools } from 'zustand/middleware'

import {
  SINNERS,
  MAX_LEVEL,
  DEFAULT_SKILL_EA,
  DUNGEON_IDX,
  migrateKeywords,
} from '@/shared/gameData'
import { createEmptyNoteContent } from '@/shared/noteEditor'
import egoSpecList from '@static/data/egoSpecList.json'

import type { ReactNode } from 'react'
import type { StoreApi } from 'zustand'
import type { MDCategory, DungeonIdx } from '@/shared/gameData'
import type {
  SinnerEquipment,
  SkillEAState,
  DeckFilterState,
  ThreadspinTier,
} from '../types/DeckTypes'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { NoteContent } from '@/shared/noteEditor'
import type { MDPlannerContent } from '../types/PlannerTypes'
import type { PlannerState } from '../hooks/usePlannerSave'

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
    selectedSinners: new Set(),
    selectedKeywords: new Set(),
    selectedAttributes: new Set(),
    selectedAtkTypes: new Set(),
    selectedDefTypes: new Set(),
    selectedRaritys: new Set(),
    selectedEgoTypes: new Set(),
    selectedSeasons: new Set(),
    selectedUnitKeywords: new Set(),
    selectedBattleKeywords: new Set(),
    searchQuery: '',
  }
}

// ============================================================================
// State Interfaces (Sliced by Mutation Frequency)
// ============================================================================

/**
 * Hot State - 70% of mutations
 * Most frequently changed fields during editing
 */
interface HotState {
  equipment: Record<string, SinnerEquipment>
  floorSelections: FloorThemeSelection[]
  comprehensiveGiftIds: Set<string>
  deploymentOrder: number[]
}

/**
 * Warm State - 25% of mutations
 * Moderately changed fields
 */
interface WarmState {
  selectedKeywords: Set<string>
  selectedBuffIds: Set<number>
  selectedGiftIds: Set<string>
  observationGiftIds: Set<string>
  selectedGiftKeyword: string | null
  skillEAState: Record<string, SkillEAState>
  deckFilterState: DeckFilterState
  /** Progressive-render counter for deck builder grids. Isolated via atomic selector so rAF ticks don't cascade to sibling sections. */
  deckVisibleCount: number
}

/**
 * Cold State - 5% of mutations
 * Rarely changed fields (metadata, config)
 */
interface ColdState {
  title: string
  category: MDCategory
  isPublished: boolean
  visibleSections: number
  sectionNotes: Record<string, NoteContent>
}

/**
 * Combined Planner Editor State
 */
export interface PlannerEditorState extends HotState, WarmState, ColdState {}

// ============================================================================
// Actions Interface
// ============================================================================

/**
 * Planner Editor Actions
 */
export interface PlannerEditorActions {
  // Hot state setters
  setEquipment: (
    equipment:
      | Record<string, SinnerEquipment>
      | ((prev: Record<string, SinnerEquipment>) => Record<string, SinnerEquipment>),
  ) => void
  updateSinnerEquipment: (sinnerId: string, equipment: SinnerEquipment) => void
  setFloorSelections: (selections: FloorThemeSelection[]) => void
  updateFloorSelection: (floorIndex: number, selection: FloorThemeSelection) => void
  setComprehensiveGiftIds: (ids: Set<string>) => void
  setDeploymentOrder: (order: number[]) => void

  // Warm state setters
  setSelectedKeywords: (keywords: Set<string>) => void
  setSelectedBuffIds: (ids: Set<number>) => void
  setSelectedGiftIds: (ids: Set<string>) => void
  setObservationGiftIds: (ids: Set<string>) => void
  setSelectedGiftKeyword: (keyword: string | null) => void
  setSkillEAState: (state: Record<string, SkillEAState>) => void
  updateSinnerSkillEA: (sinnerId: string, state: SkillEAState) => void
  setDeckFilterState: (
    state: DeckFilterState | ((prev: DeckFilterState) => DeckFilterState),
  ) => void
  setDeckVisibleCount: (count: number | ((prev: number) => number)) => void

  // Cold state setters
  setTitle: (title: string) => void
  setCategory: (category: MDCategory) => void
  setIsPublished: (published: boolean) => void
  setVisibleSections: (count: number) => void
  setSectionNotes: (notes: Record<string, NoteContent>) => void
  updateSectionNote: (sectionKey: string, content: NoteContent) => void

  // Batch operations
  initializeFromPlanner: (
    content: MDPlannerContent,
    metadata: { title: string; category: MDCategory; isPublished: boolean },
  ) => void
  reset: () => void

  // Derived state (imperative access)
  getPlannerState: () => PlannerState
}

/**
 * Combined Store Type
 */
export type PlannerEditorStore = PlannerEditorState & PlannerEditorActions

// ============================================================================
// Store Factory
// ============================================================================

/**
 * Initial state for a new planner
 */
const createInitialState = (overrides?: Partial<PlannerEditorState>): PlannerEditorState => ({
  // Hot state
  equipment: overrides?.equipment ?? createDefaultEquipment(),
  floorSelections: overrides?.floorSelections ?? createDefaultFloorSelections(),
  comprehensiveGiftIds: overrides?.comprehensiveGiftIds ?? new Set(),
  deploymentOrder: overrides?.deploymentOrder ?? [],

  // Warm state
  selectedKeywords: overrides?.selectedKeywords ?? new Set(),
  selectedBuffIds: overrides?.selectedBuffIds ?? new Set(),
  selectedGiftIds: overrides?.selectedGiftIds ?? new Set(),
  observationGiftIds: overrides?.observationGiftIds ?? new Set(),
  selectedGiftKeyword: overrides?.selectedGiftKeyword ?? null,
  skillEAState: overrides?.skillEAState ?? createDefaultSkillEAState(),
  deckFilterState: overrides?.deckFilterState ?? createDefaultDeckFilterState(),
  deckVisibleCount: overrides?.deckVisibleCount ?? 10,

  // Cold state
  title: overrides?.title ?? '',
  category: overrides?.category ?? '5F',
  isPublished: overrides?.isPublished ?? false,
  visibleSections: overrides?.visibleSections ?? 1,
  sectionNotes: overrides?.sectionNotes ?? createDefaultSectionNotes(),
})

/**
 * Creates an instance-scoped Zustand store for the planner editor
 * Use with PlannerEditorStoreProvider for component-level scoping
 *
 * @param initialState - Optional partial state to override defaults
 * @returns Zustand store instance
 */
export const createPlannerEditorStore = (initialState?: Partial<PlannerEditorState>) => {
  const state = createInitialState(initialState)

  return createStore<PlannerEditorStore>()(
    devtools(
      (set, get) => ({
        // Initial state
        ...state,

        // Hot state actions
        setEquipment: (equipment) => {
          if (typeof equipment === 'function') {
            set((state) => ({ equipment: equipment(state.equipment) }), false, 'setEquipment')
          } else {
            set({ equipment }, false, 'setEquipment')
          }
        },

        updateSinnerEquipment: (sinnerId, equipment) =>
          set(
            (state) => ({
              equipment: { ...state.equipment, [sinnerId]: equipment },
            }),
            false,
            'updateSinnerEquipment',
          ),

        setFloorSelections: (selections) =>
          set({ floorSelections: selections }, false, 'setFloorSelections'),

        updateFloorSelection: (floorIndex, selection) =>
          set(
            (state) => {
              const next = [...state.floorSelections]
              next[floorIndex] = selection
              return { floorSelections: next }
            },
            false,
            'updateFloorSelection',
          ),

        setComprehensiveGiftIds: (ids) =>
          set({ comprehensiveGiftIds: ids }, false, 'setComprehensiveGiftIds'),

        setDeploymentOrder: (order) => set({ deploymentOrder: order }, false, 'setDeploymentOrder'),

        // Warm state actions
        setSelectedKeywords: (keywords) =>
          set({ selectedKeywords: keywords }, false, 'setSelectedKeywords'),

        setSelectedBuffIds: (ids) => set({ selectedBuffIds: ids }, false, 'setSelectedBuffIds'),

        setSelectedGiftIds: (ids) => set({ selectedGiftIds: ids }, false, 'setSelectedGiftIds'),

        setObservationGiftIds: (ids) =>
          set({ observationGiftIds: ids }, false, 'setObservationGiftIds'),

        setSelectedGiftKeyword: (keyword) =>
          set({ selectedGiftKeyword: keyword }, false, 'setSelectedGiftKeyword'),

        setSkillEAState: (state) => set({ skillEAState: state }, false, 'setSkillEAState'),

        updateSinnerSkillEA: (sinnerId, skillEA) =>
          set(
            (state) => ({
              skillEAState: { ...state.skillEAState, [sinnerId]: skillEA },
            }),
            false,
            'updateSinnerSkillEA',
          ),

        setDeckFilterState: (state) => {
          if (typeof state === 'function') {
            set((s) => ({ deckFilterState: state(s.deckFilterState) }), false, 'setDeckFilterState')
          } else {
            set({ deckFilterState: state }, false, 'setDeckFilterState')
          }
        },

        setDeckVisibleCount: (count) => {
          if (typeof count === 'function') {
            set(
              (s) => ({ deckVisibleCount: count(s.deckVisibleCount) }),
              false,
              'setDeckVisibleCount',
            )
          } else {
            set({ deckVisibleCount: count }, false, 'setDeckVisibleCount')
          }
        },

        // Cold state actions
        setTitle: (title) => set({ title }, false, 'setTitle'),

        setCategory: (category) => set({ category }, false, 'setCategory'),

        setIsPublished: (published) => set({ isPublished: published }, false, 'setIsPublished'),

        setVisibleSections: (count) => set({ visibleSections: count }, false, 'setVisibleSections'),

        setSectionNotes: (notes) => set({ sectionNotes: notes }, false, 'setSectionNotes'),

        updateSectionNote: (sectionKey, content) =>
          set(
            (state) => ({
              sectionNotes: { ...state.sectionNotes, [sectionKey]: content },
            }),
            false,
            'updateSectionNote',
          ),

        // Batch operations
        initializeFromPlanner: (content, metadata) =>
          set(
            {
              // Metadata
              title: metadata.title,
              category: metadata.category,
              isPublished: metadata.isPublished,

              // Hot state - with defensive array validation
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
              deploymentOrder: Array.isArray(content.deploymentOrder)
                ? content.deploymentOrder
                : [],

              // Warm state - migrate renamed keyword ids (handles non-array input)
              selectedKeywords: new Set(migrateKeywords(content.selectedKeywords)),
              selectedBuffIds: new Set(
                Array.isArray(content.selectedBuffIds) ? content.selectedBuffIds : [],
              ),
              selectedGiftIds: new Set(
                Array.isArray(content.selectedGiftIds) ? content.selectedGiftIds : [],
              ),
              observationGiftIds: new Set(
                Array.isArray(content.observationGiftIds) ? content.observationGiftIds : [],
              ),
              selectedGiftKeyword: content.selectedGiftKeyword ?? null,
              skillEAState: content.skillEAState ?? createDefaultSkillEAState(),
              deckFilterState: createDefaultDeckFilterState(),

              // Cold state - section notes need conversion
              // Merge with defaults to backfill missing keys (e.g., intro/outro for v1 plans)
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
            },
            false,
            'initializeFromPlanner',
          ),

        reset: () => set(createInitialState(), false, 'reset'),

        // Derived state - compose PlannerState without subscription
        getPlannerState: () => {
          const s = get()
          return {
            title: s.title,
            category: s.category,
            selectedKeywords: s.selectedKeywords,
            selectedBuffIds: s.selectedBuffIds,
            selectedGiftKeyword: s.selectedGiftKeyword,
            selectedGiftIds: s.selectedGiftIds,
            observationGiftIds: s.observationGiftIds,
            comprehensiveGiftIds: s.comprehensiveGiftIds,
            equipment: s.equipment,
            deploymentOrder: s.deploymentOrder,
            skillEAState: s.skillEAState,
            floorSelections: s.floorSelections,
            sectionNotes: s.sectionNotes,
          }
        },
      }),
      { name: 'PlannerEditorStore', enabled: import.meta.env.DEV },
    ),
  )
}

// ============================================================================
// React Context & Provider
// ============================================================================

/**
 * Context for planner editor store instance
 * Allows component-level scoping of store state
 */
const PlannerEditorStoreContext = createContext<StoreApi<PlannerEditorStore> | null>(null)

/**
 * Props for PlannerEditorStoreProvider
 */
interface PlannerEditorStoreProviderProps {
  children: ReactNode
  initialState?: Partial<PlannerEditorState>
}

/**
 * Provider component for planner editor store
 * Creates a single store instance for the component tree
 *
 * @example
 * ```tsx
 * <PlannerEditorStoreProvider initialState={{ category: '15F' }}>
 *   <PlannerCreateEditor />
 * </PlannerEditorStoreProvider>
 * ```
 */
export function PlannerEditorStoreProvider({
  children,
  initialState,
}: PlannerEditorStoreProviderProps) {
  const [store] = useState(() => createPlannerEditorStore(initialState))

  return (
    <PlannerEditorStoreContext.Provider value={store}>
      {children}
    </PlannerEditorStoreContext.Provider>
  )
}

// ============================================================================
// Hooks
// ============================================================================

/**
 * Hook to access planner editor store with selector
 * Must be used within PlannerEditorStoreProvider
 *
 * @param selector - Function to select state slice
 * @returns Selected state slice
 *
 * @example
 * ```tsx
 * // Subscribe to single field (prevents cascading rerenders)
 * const equipment = usePlannerEditorStore((s) => s.equipment)
 * const setEquipment = usePlannerEditorStore((s) => s.setEquipment)
 * ```
 */
export function usePlannerEditorStore<T>(selector: (state: PlannerEditorStore) => T): T {
  const store = useContext(PlannerEditorStoreContext)

  if (!store) {
    throw new Error('usePlannerEditorStore must be used within PlannerEditorStoreProvider')
  }

  return useStore(store, selector)
}

/**
 * Stand-in store for components rendered outside a provider. One instance for
 * the whole app: it exists only so `useStore` is always called, its value is
 * never returned, and nothing ever writes to it.
 */
let placeholderStore: StoreApi<PlannerEditorStore> | null = null

function getPlaceholderStore(): StoreApi<PlannerEditorStore> {
  placeholderStore ??= createPlannerEditorStore()
  return placeholderStore
}

/**
 * Hook to safely access planner editor store with selector
 * Returns undefined if used outside PlannerEditorStoreProvider (instead of throwing)
 * Use this for components that may be rendered both inside and outside the provider
 *
 * @param selector - Function to select state slice
 * @returns Selected state slice, or undefined if outside provider
 */
export function usePlannerEditorStoreSafe<T>(
  selector: (state: PlannerEditorStore) => T,
): T | undefined {
  const store = useContext(PlannerEditorStoreContext)
  const value = useStore(store ?? getPlaceholderStore(), selector)

  return store ? value : undefined
}

/**
 * Hook to access the raw store instance
 * Use for imperative operations outside React lifecycle
 *
 * @returns Store API instance
 */
export function usePlannerEditorStoreApiSafe(): StoreApi<PlannerEditorStore> | null {
  return useContext(PlannerEditorStoreContext)
}

export function usePlannerEditorStoreApi(): StoreApi<PlannerEditorStore> {
  const store = useContext(PlannerEditorStoreContext)

  if (!store) {
    throw new Error('usePlannerEditorStoreApi must be used within PlannerEditorStoreProvider')
  }

  return store
}

// ============================================================================
// Selector Hooks (Granular Subscriptions)
// ============================================================================

export const useDeckFilterState = () => usePlannerEditorStore((s) => s.deckFilterState)
export const useDeckVisibleCount = () => usePlannerEditorStore((s) => s.deckVisibleCount)
export const useSetDeckFilterState = () => usePlannerEditorStore((s) => s.setDeckFilterState)
