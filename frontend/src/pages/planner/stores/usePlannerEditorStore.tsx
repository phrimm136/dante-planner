import { createContext, useContext, useState } from 'react'
import { createStore, useStore } from 'zustand'
import { devtools } from 'zustand/middleware'

import {
  createDefaultDeckFilterState,
  createDefaultEquipment,
  createDefaultFloorSelections,
  createDefaultSectionNotes,
  createDefaultSkillEAState,
  defaultFloorDifficulty,
  hydrateEditorState,
  projectEditorState,
} from '../lib/editorStateCodec'

import type { ReactNode } from 'react'
import type { StoreApi } from 'zustand'
import type { EncodedGiftId, MDCategory } from '@/shared/gameData'
import type { SinnerEquipment, SkillEAState, DeckFilterState } from '../types/DeckTypes'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { NoteContent } from '@/shared/noteEditor'
import type { MDPlannerContent } from '../types/PlannerTypes'
import type { PlannerState } from '../lib/saveablePlanner'

export {
  createDefaultDeckFilterState,
  createDefaultEquipment,
  createDefaultFloorSelections,
  createDefaultSectionNotes,
  createDefaultSkillEAState,
}

interface HotState {
  equipment: Record<string, SinnerEquipment>
  floorSelections: FloorThemeSelection[]
  comprehensiveGiftIds: Set<EncodedGiftId>
  deploymentOrder: number[]
}

interface WarmState {
  selectedKeywords: Set<string>
  selectedBuffIds: Set<number>
  selectedGiftIds: Set<EncodedGiftId>
  observationGiftIds: Set<EncodedGiftId>
  selectedGiftKeyword: string | null
  skillEAState: Record<string, SkillEAState>
  deckFilterState: DeckFilterState
  deckVisibleCount: number
}

interface ColdState {
  title: string
  category: MDCategory
  isPublished: boolean
  visibleSections: number
  sectionNotes: Record<string, NoteContent>
}

export interface PlannerEditorState extends HotState, WarmState, ColdState {}

export interface PlannerEditorActions {
  setEquipment: (
    equipment:
      | Record<string, SinnerEquipment>
      | ((prev: Record<string, SinnerEquipment>) => Record<string, SinnerEquipment>),
  ) => void
  updateSinnerEquipment: (sinnerId: string, equipment: SinnerEquipment) => void
  setFloorSelections: (selections: FloorThemeSelection[]) => void
  updateFloorSelection: (floorIndex: number, selection: FloorThemeSelection) => void
  setComprehensiveGiftIds: (ids: Set<EncodedGiftId>) => void
  setDeploymentOrder: (order: number[]) => void

  setSelectedKeywords: (keywords: Set<string>) => void
  setSelectedBuffIds: (ids: Set<number>) => void
  setSelectedGiftIds: (ids: Set<EncodedGiftId>) => void
  setObservationGiftIds: (ids: Set<EncodedGiftId>) => void
  setSelectedGiftKeyword: (keyword: string | null) => void
  setSkillEAState: (state: Record<string, SkillEAState>) => void
  updateSinnerSkillEA: (sinnerId: string, state: SkillEAState) => void
  setDeckFilterState: (
    state: DeckFilterState | ((prev: DeckFilterState) => DeckFilterState),
  ) => void
  setDeckVisibleCount: (count: number | ((prev: number) => number)) => void

  setTitle: (title: string) => void
  setCategory: (category: MDCategory) => void
  setIsPublished: (published: boolean) => void
  setVisibleSections: (count: number) => void
  setSectionNotes: (notes: Record<string, NoteContent>) => void
  updateSectionNote: (sectionKey: string, content: NoteContent) => void

  initializeFromPlanner: (
    content: MDPlannerContent,
    metadata: { title: string; category: MDCategory; isPublished: boolean },
  ) => void
  reset: () => void

  getPlannerState: () => PlannerState
}

export type PlannerEditorStore = PlannerEditorState & PlannerEditorActions

const createInitialState = (overrides?: Partial<PlannerEditorState>): PlannerEditorState => {
  const category = overrides?.category ?? '5F'
  return {
    equipment: overrides?.equipment ?? createDefaultEquipment(),
    floorSelections: overrides?.floorSelections ?? createDefaultFloorSelections(category),
    comprehensiveGiftIds: overrides?.comprehensiveGiftIds ?? new Set(),
    deploymentOrder: overrides?.deploymentOrder ?? [],

    selectedKeywords: overrides?.selectedKeywords ?? new Set(),
    selectedBuffIds: overrides?.selectedBuffIds ?? new Set(),
    selectedGiftIds: overrides?.selectedGiftIds ?? new Set(),
    observationGiftIds: overrides?.observationGiftIds ?? new Set(),
    selectedGiftKeyword: overrides?.selectedGiftKeyword ?? null,
    skillEAState: overrides?.skillEAState ?? createDefaultSkillEAState(),
    deckFilterState: overrides?.deckFilterState ?? createDefaultDeckFilterState(),
    deckVisibleCount: overrides?.deckVisibleCount ?? 10,

    title: overrides?.title ?? '',
    category,
    isPublished: overrides?.isPublished ?? false,
    visibleSections: overrides?.visibleSections ?? 1,
    sectionNotes: overrides?.sectionNotes ?? createDefaultSectionNotes(),
  }
}

export const createPlannerEditorStore = (initialState?: Partial<PlannerEditorState>) => {
  const state = createInitialState(initialState)

  return createStore<PlannerEditorStore>()(
    devtools(
      (set, get) => ({
        ...state,

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

        setTitle: (title) => set({ title }, false, 'setTitle'),

        setCategory: (category) =>
          set(
            (state) => ({
              category,
              floorSelections: state.floorSelections.map((floor, floorIndex) =>
                floor.themePackId === null
                  ? { ...floor, difficulty: defaultFloorDifficulty(category, floorIndex) }
                  : floor,
              ),
            }),
            false,
            'setCategory',
          ),

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

        initializeFromPlanner: (content, metadata) =>
          set(hydrateEditorState(content, metadata), false, 'initializeFromPlanner'),

        reset: () => set(createInitialState(), false, 'reset'),

        getPlannerState: () => projectEditorState(get()),
      }),
      { name: 'PlannerEditorStore', enabled: import.meta.env.DEV },
    ),
  )
}

const PlannerEditorStoreContext = createContext<StoreApi<PlannerEditorStore> | null>(null)

interface PlannerEditorStoreProviderProps {
  children: ReactNode
  initialState?: Partial<PlannerEditorState> | undefined
}

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

export function usePlannerEditorStore<T>(selector: (state: PlannerEditorStore) => T): T {
  const store = useContext(PlannerEditorStoreContext)

  if (!store) {
    throw new Error('usePlannerEditorStore must be used within PlannerEditorStoreProvider')
  }

  return useStore(store, selector)
}

let placeholderStore: StoreApi<PlannerEditorStore> | null = null

function getPlaceholderStore(): StoreApi<PlannerEditorStore> {
  placeholderStore ??= createPlannerEditorStore()
  return placeholderStore
}

export function usePlannerEditorStoreSafe<T>(
  selector: (state: PlannerEditorStore) => T,
): T | undefined {
  const store = useContext(PlannerEditorStoreContext)
  const value = useStore(store ?? getPlaceholderStore(), selector)

  return store ? value : undefined
}

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

export const useDeckFilterState = () => usePlannerEditorStore((s) => s.deckFilterState)
export const useDeckVisibleCount = () => usePlannerEditorStore((s) => s.deckVisibleCount)
export const useSetDeckFilterState = () => usePlannerEditorStore((s) => s.setDeckFilterState)
