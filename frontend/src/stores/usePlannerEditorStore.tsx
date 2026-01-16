import { createContext, useContext, useRef } from 'react'
import { createStore, useStore } from 'zustand'
import { devtools } from 'zustand/middleware'
import { useShallow } from 'zustand/shallow'

import {
  SINNERS,
  MAX_LEVEL,
  DEFAULT_SKILL_EA,
  DUNGEON_IDX,
} from '@/lib/constants'
import { createEmptyNoteContent } from '@/schemas/NoteEditorSchemas'

import type { ReactNode } from 'react'
import type { StoreApi } from 'zustand'
import type { MDCategory, DungeonIdx } from '@/lib/constants'
import type { SinnerEquipment, SkillEAState, DeckFilterState } from '@/types/DeckTypes'
import type { FloorThemeSelection } from '@/types/ThemePackTypes'
import type { NoteContent } from '@/types/NoteEditorTypes'
import type { MDPlannerContent } from '@/types/PlannerTypes'
import type { PlannerState } from '@/hooks/usePlannerSave'

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
        ZAYIN: { id: defaultEgoId, threadspin: 4 },
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
    deckBuilder: createEmptyNoteContent(),
    startBuffs: createEmptyNoteContent(),
    startGifts: createEmptyNoteContent(),
    observation: createEmptyNoteContent(),
    skillReplacement: createEmptyNoteContent(),
    comprehensiveGifts: createEmptyNoteContent(),
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
  setEquipment: (equipment: Record<string, SinnerEquipment>) => void
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
  setDeckFilterState: (state: DeckFilterState) => void

  // Cold state setters
  setTitle: (title: string) => void
  setCategory: (category: MDCategory) => void
  setIsPublished: (published: boolean) => void
  setVisibleSections: (count: number) => void
  setSectionNotes: (notes: Record<string, NoteContent>) => void
  updateSectionNote: (sectionKey: string, content: NoteContent) => void

  // Batch operations
  initializeFromPlanner: (content: MDPlannerContent, metadata: { title: string; category: MDCategory; isPublished: boolean }) => void
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
        setEquipment: (equipment) => set({ equipment }, false, 'setEquipment'),

        updateSinnerEquipment: (sinnerId, equipment) =>
          set(
            (state) => ({
              equipment: { ...state.equipment, [sinnerId]: equipment },
            }),
            false,
            'updateSinnerEquipment'
          ),

        setFloorSelections: (selections) => set({ floorSelections: selections }, false, 'setFloorSelections'),

        updateFloorSelection: (floorIndex, selection) =>
          set(
            (state) => {
              const next = [...state.floorSelections]
              next[floorIndex] = selection
              return { floorSelections: next }
            },
            false,
            'updateFloorSelection'
          ),

        setComprehensiveGiftIds: (ids) => set({ comprehensiveGiftIds: ids }, false, 'setComprehensiveGiftIds'),

        setDeploymentOrder: (order) => set({ deploymentOrder: order }, false, 'setDeploymentOrder'),

        // Warm state actions
        setSelectedKeywords: (keywords) => set({ selectedKeywords: keywords }, false, 'setSelectedKeywords'),

        setSelectedBuffIds: (ids) => set({ selectedBuffIds: ids }, false, 'setSelectedBuffIds'),

        setSelectedGiftIds: (ids) => set({ selectedGiftIds: ids }, false, 'setSelectedGiftIds'),

        setObservationGiftIds: (ids) => set({ observationGiftIds: ids }, false, 'setObservationGiftIds'),

        setSelectedGiftKeyword: (keyword) => set({ selectedGiftKeyword: keyword }, false, 'setSelectedGiftKeyword'),

        setSkillEAState: (state) => set({ skillEAState: state }, false, 'setSkillEAState'),

        updateSinnerSkillEA: (sinnerId, skillEA) =>
          set(
            (state) => ({
              skillEAState: { ...state.skillEAState, [sinnerId]: skillEA },
            }),
            false,
            'updateSinnerSkillEA'
          ),

        setDeckFilterState: (state) => set({ deckFilterState: state }, false, 'setDeckFilterState'),

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
            'updateSectionNote'
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
              comprehensiveGiftIds: new Set(Array.isArray(content.comprehensiveGiftIds) ? content.comprehensiveGiftIds : []),
              deploymentOrder: Array.isArray(content.deploymentOrder) ? content.deploymentOrder : [],

              // Warm state - with defensive array validation
              selectedKeywords: new Set(Array.isArray(content.selectedKeywords) ? content.selectedKeywords : []),
              selectedBuffIds: new Set(Array.isArray(content.selectedBuffIds) ? content.selectedBuffIds : []),
              selectedGiftIds: new Set(Array.isArray(content.selectedGiftIds) ? content.selectedGiftIds : []),
              observationGiftIds: new Set(Array.isArray(content.observationGiftIds) ? content.observationGiftIds : []),
              selectedGiftKeyword: content.selectedGiftKeyword ?? null,
              skillEAState: content.skillEAState ?? createDefaultSkillEAState(),

              // Cold state - section notes need conversion
              sectionNotes: content.sectionNotes
                ? Object.fromEntries(
                    Object.entries(content.sectionNotes).map(([key, note]) => [
                      key,
                      { content: note?.content ?? '' },
                    ])
                  )
                : createDefaultSectionNotes(),
            },
            false,
            'initializeFromPlanner'
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
      { name: 'PlannerEditorStore', enabled: import.meta.env.DEV }
    )
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
 *   <PlannerMDEditorContent mode="new" />
 * </PlannerEditorStoreProvider>
 * ```
 */
export function PlannerEditorStoreProvider({ children, initialState }: PlannerEditorStoreProviderProps) {
  const storeRef = useRef<StoreApi<PlannerEditorStore> | null>(null)

  if (!storeRef.current) {
    storeRef.current = createPlannerEditorStore(initialState)
  }

  return (
    <PlannerEditorStoreContext.Provider value={storeRef.current}>
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
 * Hook to check if inside PlannerEditorStoreProvider context
 * Use this to conditionally render store-dependent components
 *
 * @returns true if inside provider, false otherwise
 */
export function useIsInPlannerEditorContext(): boolean {
  const store = useContext(PlannerEditorStoreContext)
  return store !== null
}

/**
 * Hook to safely access planner editor store with selector
 * Returns undefined if used outside PlannerEditorStoreProvider (instead of throwing)
 * Use this for components that may be rendered both inside and outside the provider
 *
 * @param selector - Function to select state slice
 * @returns Selected state slice, or undefined if outside provider
 */
export function usePlannerEditorStoreSafe<T>(selector: (state: PlannerEditorStore) => T): T | undefined {
  const store = useContext(PlannerEditorStoreContext)

  // Create a stable dummy store for when we're outside context
  // This satisfies the Rules of Hooks (always call useStore)
  const dummyStore = useRef<StoreApi<PlannerEditorStore> | null>(null)
  if (!dummyStore.current && !store) {
    // Create a minimal store just to satisfy useStore
    dummyStore.current = createPlannerEditorStore()
  }

  const effectiveStore = store ?? dummyStore.current!
  const value = useStore(effectiveStore, selector)

  // Return undefined if we're not in a real context
  if (!store) {
    return undefined
  }

  return value
}

/**
 * Hook to access the raw store instance
 * Use for imperative operations outside React lifecycle
 *
 * @returns Store API instance
 */
export function usePlannerEditorStoreApi(): StoreApi<PlannerEditorStore> {
  const store = useContext(PlannerEditorStoreContext)

  if (!store) {
    throw new Error('usePlannerEditorStoreApi must be used within PlannerEditorStoreProvider')
  }

  return store
}

/**
 * Hook for save logic - single subscription with shallow equality
 * Returns composed PlannerState for usePlannerSave
 *
 * Uses Zustand's shallow equality to prevent re-renders when values
 * haven't actually changed. This replaces 15+ individual subscriptions
 * with ONE subscription that only triggers re-render on actual changes.
 *
 * @returns PlannerState composed from current store state
 */
export function usePlannerStateForSave(): PlannerState {
  const store = useContext(PlannerEditorStoreContext)

  if (!store) {
    throw new Error('usePlannerStateForSave must be used within PlannerEditorStoreProvider')
  }

  // Single subscription with shallow equality comparison
  // Re-renders only when actual values change (not object reference)
  return useStore(
    store,
    useShallow((s) => ({
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
    }))
  )
}

// ============================================================================
// Selector Hooks (Granular Subscriptions)
// ============================================================================

/**
 * Pre-built selector hooks for common state slices.
 *
 * These are OPTIONAL convenience exports - components can also use
 * `usePlannerEditorStore((s) => s.fieldName)` directly for custom selectors.
 *
 * Benefits of pre-built selectors:
 * - Consistent subscription granularity across components
 * - Discoverable API for store consumers
 * - TypeScript autocomplete support
 *
 * Usage pattern:
 * ```tsx
 * // Option 1: Pre-built selector (recommended for common fields)
 * const equipment = useEquipment()
 *
 * // Option 2: Inline selector (for custom/combined selections)
 * const sinnerEquipment = usePlannerEditorStore((s) => s.equipment[sinnerId])
 * ```
 */

// Hot state selectors
export const useEquipment = () => usePlannerEditorStore((s) => s.equipment)
export const useFloorSelections = () => usePlannerEditorStore((s) => s.floorSelections)
export const useComprehensiveGiftIds = () => usePlannerEditorStore((s) => s.comprehensiveGiftIds)
export const useDeploymentOrder = () => usePlannerEditorStore((s) => s.deploymentOrder)

// Warm state selectors
export const useSelectedKeywords = () => usePlannerEditorStore((s) => s.selectedKeywords)
export const useSelectedBuffIds = () => usePlannerEditorStore((s) => s.selectedBuffIds)
export const useSelectedGiftIds = () => usePlannerEditorStore((s) => s.selectedGiftIds)
export const useObservationGiftIds = () => usePlannerEditorStore((s) => s.observationGiftIds)
export const useSelectedGiftKeyword = () => usePlannerEditorStore((s) => s.selectedGiftKeyword)
export const useSkillEAState = () => usePlannerEditorStore((s) => s.skillEAState)
export const useDeckFilterState = () => usePlannerEditorStore((s) => s.deckFilterState)

// Cold state selectors
export const usePlannerTitle = () => usePlannerEditorStore((s) => s.title)
export const usePlannerCategory = () => usePlannerEditorStore((s) => s.category)
export const useIsPublished = () => usePlannerEditorStore((s) => s.isPublished)
export const useVisibleSections = () => usePlannerEditorStore((s) => s.visibleSections)
export const useSectionNotes = () => usePlannerEditorStore((s) => s.sectionNotes)

// Action selectors
export const useSetEquipment = () => usePlannerEditorStore((s) => s.setEquipment)
export const useUpdateSinnerEquipment = () => usePlannerEditorStore((s) => s.updateSinnerEquipment)
export const useSetFloorSelections = () => usePlannerEditorStore((s) => s.setFloorSelections)
export const useUpdateFloorSelection = () => usePlannerEditorStore((s) => s.updateFloorSelection)
export const useSetComprehensiveGiftIds = () => usePlannerEditorStore((s) => s.setComprehensiveGiftIds)
export const useSetDeploymentOrder = () => usePlannerEditorStore((s) => s.setDeploymentOrder)
export const useSetSelectedKeywords = () => usePlannerEditorStore((s) => s.setSelectedKeywords)
export const useSetSelectedBuffIds = () => usePlannerEditorStore((s) => s.setSelectedBuffIds)
export const useSetSelectedGiftIds = () => usePlannerEditorStore((s) => s.setSelectedGiftIds)
export const useSetObservationGiftIds = () => usePlannerEditorStore((s) => s.setObservationGiftIds)
export const useSetSelectedGiftKeyword = () => usePlannerEditorStore((s) => s.setSelectedGiftKeyword)
export const useSetSkillEAState = () => usePlannerEditorStore((s) => s.setSkillEAState)
export const useUpdateSinnerSkillEA = () => usePlannerEditorStore((s) => s.updateSinnerSkillEA)
export const useSetDeckFilterState = () => usePlannerEditorStore((s) => s.setDeckFilterState)
export const useSetTitle = () => usePlannerEditorStore((s) => s.setTitle)
export const useSetCategory = () => usePlannerEditorStore((s) => s.setCategory)
export const useSetIsPublished = () => usePlannerEditorStore((s) => s.setIsPublished)
export const useSetVisibleSections = () => usePlannerEditorStore((s) => s.setVisibleSections)
export const useSetSectionNotes = () => usePlannerEditorStore((s) => s.setSectionNotes)
export const useUpdateSectionNote = () => usePlannerEditorStore((s) => s.updateSectionNote)
export const useInitializeFromPlanner = () => usePlannerEditorStore((s) => s.initializeFromPlanner)
export const useResetStore = () => usePlannerEditorStore((s) => s.reset)
