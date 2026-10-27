import { createContext, useContext, useState } from 'react'
import { createStore, useStore } from 'zustand'
import { devtools } from 'zustand/middleware'
import { MIN_LEVEL, floorCount } from '@/shared/gameData'

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
import { collectOwnedGiftIds } from '../lib/deckEA'

import type { ReactNode } from 'react'
import type { StoreApi } from 'zustand'
import type { EGOGiftId, EncodedGiftId, MDCategory } from '@/shared/gameData'
import type { SinnerEquipment, SkillEAState, DeckFilterState } from '../types/DeckTypes'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { NoteContent } from '@/shared/noteEditor'
import type { MDPlannerContent } from '../types/PlannerTypes'
import type { PlannerState } from '../lib/saveablePlanner'
import type { DeckBuilderDeck } from '../types/DeckTypes'

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

export interface PlannerStoreState extends HotState, WarmState, ColdState {}

export interface PlannerStoreActions {
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

export type PlannerStore = PlannerStoreState & {
  identityMaxLevel: number
} & PlannerStoreActions

const createInitialState = (
  identityMaxLevel: number,
  overrides?: Partial<PlannerStoreState>,
): PlannerStoreState => {
  const category = overrides?.category ?? '5F'
  return {
    equipment: overrides?.equipment ?? createDefaultEquipment(identityMaxLevel),
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

export const createPlannerStore = (
  identityMaxLevel: number,
  initialState?: Partial<PlannerStoreState>,
) => {
  const state = createInitialState(identityMaxLevel, initialState)

  return createStore<PlannerStore>()(
    devtools(
      (set, get) => ({
        ...state,
        identityMaxLevel,

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
          set(
            hydrateEditorState(content, metadata, identityMaxLevel),
            false,
            'initializeFromPlanner',
          ),

        reset: () => set(createInitialState(identityMaxLevel), false, 'reset'),

        getPlannerState: () => projectEditorState(get()),
      }),
      { name: 'PlannerStore', enabled: import.meta.env.DEV },
    ),
  )
}

const PlannerStoreContext = createContext<StoreApi<PlannerStore> | null>(null)

interface PlannerStoreProviderProps {
  children: ReactNode
  identityMaxLevel: number
  initialState?: Partial<PlannerStoreState> | undefined
}

export function PlannerStoreProvider({
  children,
  identityMaxLevel,
  initialState,
}: PlannerStoreProviderProps) {
  const [store] = useState(() => createPlannerStore(identityMaxLevel, initialState))

  return <PlannerStoreContext.Provider value={store}>{children}</PlannerStoreContext.Provider>
}

export function usePlannerStore<T>(selector: (state: PlannerStore) => T): T {
  const store = useContext(PlannerStoreContext)

  if (!store) {
    throw new Error('usePlannerStore must be used within PlannerStoreProvider')
  }

  return useStore(store, selector)
}

let placeholderStore: StoreApi<PlannerStore> | null = null

function getPlaceholderStore(): StoreApi<PlannerStore> {
  placeholderStore ??= createPlannerStore(MIN_LEVEL)
  return placeholderStore
}

export function usePlannerStoreSafe<T>(selector: (state: PlannerStore) => T): T | undefined {
  const store = useContext(PlannerStoreContext)
  const value = useStore(store ?? getPlaceholderStore(), selector)

  return store ? value : undefined
}

export function usePlannerStoreApiSafe(): StoreApi<PlannerStore> | null {
  return useContext(PlannerStoreContext)
}

export function usePlannerStoreApi(): StoreApi<PlannerStore> {
  const store = useContext(PlannerStoreContext)

  if (!store) {
    throw new Error('usePlannerStoreApi must be used within PlannerStoreProvider')
  }

  return store
}

export const useDeckFilterState = () => usePlannerStore((s) => s.deckFilterState)
export const useDeckVisibleCount = () => usePlannerStore((s) => s.deckVisibleCount)
export const useSetDeckFilterState = () => usePlannerStore((s) => s.setDeckFilterState)

export function useStoreDeck(): DeckBuilderDeck {
  const equipment = usePlannerStore((s) => s.equipment)
  const setEquipment = usePlannerStore((s) => s.setEquipment)
  const deploymentOrder = usePlannerStore((s) => s.deploymentOrder)
  const setDeploymentOrder = usePlannerStore((s) => s.setDeploymentOrder)

  return { equipment, setEquipment, deploymentOrder, setDeploymentOrder }
}

export function useStoreOwnedGiftIds(): ReadonlySet<EGOGiftId> {
  const selectedGiftIds = usePlannerStore((s) => s.selectedGiftIds)
  const observationGiftIds = usePlannerStore((s) => s.observationGiftIds)
  const comprehensiveGiftIds = usePlannerStore((s) => s.comprehensiveGiftIds)
  const floorSelections = usePlannerStore((s) => s.floorSelections)
  const category = usePlannerStore((s) => s.category)

  return collectOwnedGiftIds(
    { selectedGiftIds, observationGiftIds, comprehensiveGiftIds, floorSelections },
    floorCount(category),
  )
}
