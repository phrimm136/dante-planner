/**
 * usePlannerEditorStore.test.tsx
 *
 * Characterization (golden-master) tests for the planner editor Zustand store.
 * Pins CURRENT observable behavior of the public actions and the Set<->array
 * serialization edges in initializeFromPlanner / getPlannerState. No mocks:
 * the store is vanilla Zustand, so it is driven directly via createPlannerEditorStore().
 */

import { describe, it, expect } from 'vitest'
import {
  createPlannerEditorStore,
  createDefaultEquipment,
  createDefaultSkillEAState,
  createDefaultFloorSelections,
  createDefaultDeckFilterState,
} from '../usePlannerEditorStore'
import { createEmptyNoteContent } from '@/shared/noteEditor'
import { DUNGEON_IDX } from '@/shared/gameData'
import type { MDPlannerContent } from '../../types/PlannerTypes'
import type { SinnerEquipment, SkillEAState } from '../../types/DeckTypes'

describe('usePlannerEditorStore', () => {
  describe('initial state', () => {
    it('starts with default equipment, skill EA, floor selections, and empty cold fields', () => {
      const store = createPlannerEditorStore()
      const s = store.getState()

      expect(s.equipment).toEqual(createDefaultEquipment())
      expect(s.skillEAState).toEqual(createDefaultSkillEAState())
      expect(s.floorSelections).toHaveLength(15)
      expect(s.comprehensiveGiftIds).toBeInstanceOf(Set)
      expect(s.comprehensiveGiftIds.size).toBe(0)
      expect(s.deploymentOrder).toEqual([])
      expect(s.title).toBe('')
      expect(s.category).toBe('5F')
      expect(s.isPublished).toBe(false)
      expect(s.visibleSections).toBe(1)
      expect(s.deckVisibleCount).toBe(10)
    })

    it('applies initial-state overrides over defaults', () => {
      const store = createPlannerEditorStore({
        title: 'Preset',
        category: '15F',
        isPublished: true,
      })
      const s = store.getState()

      expect(s.title).toBe('Preset')
      expect(s.category).toBe('15F')
      expect(s.isPublished).toBe(true)
    })
  })

  describe('equipment actions', () => {
    it('setEquipment replaces the whole equipment map', () => {
      const store = createPlannerEditorStore()
      const replacement: Record<string, SinnerEquipment> = {
        '1': { identity: { id: '10101', uptie: 4, level: 50 }, egos: {} },
      }

      store.getState().setEquipment(replacement)

      expect(store.getState().equipment).toEqual(replacement)
    })

    it('setEquipment accepts an updater function', () => {
      const store = createPlannerEditorStore()

      store
        .getState()
        .setEquipment((prev) => ({
          ...prev,
          '99': { identity: { id: 'X', uptie: 4, level: 1 }, egos: {} },
        }))

      expect(store.getState().equipment['99']).toEqual({
        identity: { id: 'X', uptie: 4, level: 1 },
        egos: {},
      })
    })

    it('updateSinnerEquipment replaces a single sinner entry without dropping others', () => {
      const store = createPlannerEditorStore()
      const before = store.getState().equipment
      const next: SinnerEquipment = { identity: { id: '10199', uptie: 4, level: 1 }, egos: {} }

      store.getState().updateSinnerEquipment('1', next)

      const after = store.getState().equipment
      expect(after['1']).toEqual(next)
      expect(after['2']).toEqual(before['2'])
    })
  })

  describe('floor selection actions', () => {
    it('setFloorSelections replaces the array', () => {
      const store = createPlannerEditorStore()
      const selections = createDefaultFloorSelections()

      store.getState().setFloorSelections(selections)

      expect(store.getState().floorSelections).toBe(selections)
    })

    it('updateFloorSelection replaces a single floor by index', () => {
      const store = createPlannerEditorStore()
      const updated = {
        themePackId: 'pack-1',
        difficulty: DUNGEON_IDX.HARD,
        giftIds: new Set(['g1']),
      }

      store.getState().updateFloorSelection(3, updated)

      const floors = store.getState().floorSelections
      expect(floors[3]).toEqual(updated)
      expect(floors[0].themePackId).toBeNull()
    })
  })

  describe('deployment order action', () => {
    it('setDeploymentOrder replaces the array', () => {
      const store = createPlannerEditorStore()

      store.getState().setDeploymentOrder([2, 0, 1])

      expect(store.getState().deploymentOrder).toEqual([2, 0, 1])
    })
  })

  describe('warm + cold state setters', () => {
    it('setSelectedKeywords / setComprehensiveGiftIds store Set instances as-is', () => {
      const store = createPlannerEditorStore()
      const keywords = new Set(['k1', 'k2'])
      const gifts = new Set(['g1'])

      store.getState().setSelectedKeywords(keywords)
      store.getState().setComprehensiveGiftIds(gifts)

      expect(store.getState().selectedKeywords).toBe(keywords)
      expect(store.getState().comprehensiveGiftIds).toBe(gifts)
    })

    it('setSelectedGiftKeyword accepts null', () => {
      const store = createPlannerEditorStore()

      store.getState().setSelectedGiftKeyword('fire')
      expect(store.getState().selectedGiftKeyword).toBe('fire')

      store.getState().setSelectedGiftKeyword(null)
      expect(store.getState().selectedGiftKeyword).toBeNull()
    })

    it('setDeckVisibleCount accepts a value and an updater', () => {
      const store = createPlannerEditorStore()

      store.getState().setDeckVisibleCount(20)
      expect(store.getState().deckVisibleCount).toBe(20)

      store.getState().setDeckVisibleCount((prev) => prev + 5)
      expect(store.getState().deckVisibleCount).toBe(25)
    })

    it('setTitle / setCategory / setIsPublished update cold state', () => {
      const store = createPlannerEditorStore()

      store.getState().setTitle('My Plan')
      store.getState().setCategory('10F')
      store.getState().setIsPublished(true)

      const s = store.getState()
      expect(s.title).toBe('My Plan')
      expect(s.category).toBe('10F')
      expect(s.isPublished).toBe(true)
    })

    it('updateSinnerSkillEA replaces a single sinner skill entry', () => {
      const store = createPlannerEditorStore()
      const next: SkillEAState = { 0: 4, 1: 1, 2: 1 }

      store.getState().updateSinnerSkillEA('1', next)

      expect(store.getState().skillEAState['1']).toEqual(next)
    })

    it('updateSectionNote replaces a single section note', () => {
      const store = createPlannerEditorStore()
      const note = { content: { type: 'doc', content: [] } }

      store.getState().updateSectionNote('intro', note)

      expect(store.getState().sectionNotes.intro).toEqual(note)
    })
  })

  describe('initializeFromPlanner (array -> Set deserialization)', () => {
    function makeContent(overrides: Partial<MDPlannerContent> = {}): MDPlannerContent {
      return {
        type: 'MIRROR_DUNGEON',
        selectedKeywords: ['k1'],
        selectedBuffIds: [3],
        selectedGiftKeyword: 'fire',
        selectedGiftIds: ['g1', 'g2'],
        observationGiftIds: ['o1'],
        comprehensiveGiftIds: ['c1'],
        equipment: createDefaultEquipment(),
        deploymentOrder: [0, 1, 2],
        skillEAState: createDefaultSkillEAState(),
        floorSelections: [
          { themePackId: 'pack-1', difficulty: DUNGEON_IDX.NORMAL, giftIds: ['fg1', 'fg2'] },
        ],
        sectionNotes: { intro: { content: 'note' } },
        ...overrides,
      } as unknown as MDPlannerContent
    }

    it('converts serialized arrays into Set instances', () => {
      const store = createPlannerEditorStore()

      store
        .getState()
        .initializeFromPlanner(makeContent(), { title: 'T', category: '5F', isPublished: true })

      const s = store.getState()
      expect(s.selectedKeywords).toBeInstanceOf(Set)
      expect(Array.from(s.selectedKeywords)).toEqual(['k1'])
      expect(Array.from(s.selectedBuffIds)).toEqual([3])
      expect(Array.from(s.selectedGiftIds)).toEqual(['g1', 'g2'])
      expect(Array.from(s.observationGiftIds)).toEqual(['o1'])
      expect(Array.from(s.comprehensiveGiftIds)).toEqual(['c1'])
      expect(s.floorSelections[0].giftIds).toBeInstanceOf(Set)
      expect(Array.from(s.floorSelections[0].giftIds)).toEqual(['fg1', 'fg2'])
      expect(s.title).toBe('T')
      expect(s.isPublished).toBe(true)
    })

    it('falls back to defaults when array fields are missing or non-arrays', () => {
      const store = createPlannerEditorStore()

      store.getState().initializeFromPlanner(
        makeContent({
          selectedKeywords: undefined as unknown as string[],
          floorSelections: undefined as unknown as MDPlannerContent['floorSelections'],
          deploymentOrder: undefined as unknown as number[],
          equipment: undefined as unknown as MDPlannerContent['equipment'],
        }),
        { title: 'T', category: '5F', isPublished: false },
      )

      const s = store.getState()
      expect(s.selectedKeywords.size).toBe(0)
      expect(s.deploymentOrder).toEqual([])
      expect(s.floorSelections).toHaveLength(15)
      expect(s.equipment).toEqual(createDefaultEquipment())
    })

    it('backfills missing section-note keys from defaults and overrides supplied keys', () => {
      const store = createPlannerEditorStore()
      const defaultEmpty = createEmptyNoteContent()

      store
        .getState()
        .initializeFromPlanner(
          makeContent({
            sectionNotes: {
              intro: { content: 'only-intro' },
            } as unknown as MDPlannerContent['sectionNotes'],
          }),
          { title: 'T', category: '5F', isPublished: false },
        )

      const notes = store.getState().sectionNotes
      // Supplied key is taken verbatim (content ?? '')
      expect(notes.intro).toEqual({ content: 'only-intro' })
      // Missing keys retain the default empty-note backfill
      expect(notes.outro).toEqual(defaultEmpty)
      expect(notes['floor-0']).toEqual(defaultEmpty)
    })

    it('resets deckFilterState to the default regardless of prior value', () => {
      // Warning: initializeFromPlanner hard-resets deckFilterState (no content field
      // feeds it); a non-default filter set before hydrate is discarded.
      const store = createPlannerEditorStore()
      store.getState().setDeckFilterState({
        ...createDefaultDeckFilterState(),
        entityMode: 'ego',
        searchQuery: 'pierce',
        selectedSinners: new Set(['1', '2']),
      })

      store
        .getState()
        .initializeFromPlanner(makeContent(), { title: 'T', category: '5F', isPublished: false })

      expect(store.getState().deckFilterState).toEqual(createDefaultDeckFilterState())
    })
  })

  describe('getPlannerState (Set round-trip)', () => {
    it('returns Set instances and the live state fields', () => {
      const store = createPlannerEditorStore()
      store.getState().setSelectedKeywords(new Set(['k1']))
      store.getState().setTitle('Round Trip')

      const planner = store.getState().getPlannerState()

      expect(planner.title).toBe('Round Trip')
      expect(planner.selectedKeywords).toBeInstanceOf(Set)
      expect(Array.from(planner.selectedKeywords)).toEqual(['k1'])
      expect(planner.comprehensiveGiftIds).toBeInstanceOf(Set)
      expect(planner.deploymentOrder).toEqual([])
    })

    it('preserves floorSelections[i].giftIds as a Set with the original ids', () => {
      const store = createPlannerEditorStore()
      store.getState().updateFloorSelection(2, {
        themePackId: 'pack-2',
        difficulty: DUNGEON_IDX.HARD,
        giftIds: new Set(['fg1', 'fg2']),
      })

      const planner = store.getState().getPlannerState()

      expect(planner.floorSelections[2].giftIds).toBeInstanceOf(Set)
      expect(Array.from(planner.floorSelections[2].giftIds)).toEqual(['fg1', 'fg2'])
    })
  })

  describe('reset', () => {
    it('restores defaults after mutations', () => {
      const store = createPlannerEditorStore()
      store.getState().setTitle('Dirty')
      store.getState().setDeploymentOrder([5, 4, 3])
      store.getState().setComprehensiveGiftIds(new Set(['g1']))

      store.getState().reset()

      const s = store.getState()
      expect(s.title).toBe('')
      expect(s.deploymentOrder).toEqual([])
      expect(s.comprehensiveGiftIds.size).toBe(0)
      expect(s.equipment).toEqual(createDefaultEquipment())
    })
  })
})
