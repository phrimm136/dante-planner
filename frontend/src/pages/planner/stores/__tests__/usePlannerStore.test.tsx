/**
 * usePlannerStore.test.tsx
 *
 * Characterization (golden-master) tests for the planner editor Zustand store.
 * Pins CURRENT observable behavior of the public actions and the Set<->array
 * serialization edges in initializeFromPlanner / getPlannerState. The store
 * is vanilla Zustand, so it is driven directly via createPlannerStore().
 */

import { describe, it, expect, assert, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import {
  PlannerStoreProvider,
  useStoreOwnedGiftIds,
  useStoreSeason,
  createPlannerStore,
  createDefaultEquipment,
  createDefaultSkillEAState,
  createDefaultDeckFilterState,
} from '../usePlannerStore'
import { createEmptyNoteContent } from '@/shared/noteEditor'
import { DUNGEON_IDX, floorCount } from '@/shared/gameData'
import { hydrateEditorState } from '../../lib/editorStateCodec'
import { collectOwnedGiftIds } from '../../lib/deckEA'
import { deserializeSets } from '../../schemas/PlannerSchemas'
import type { MDPlannerContent } from '../../types/PlannerTypes'
import type { SinnerEquipment, SkillEAState } from '../../types/DeckTypes'
import { ThemePackIdSchema } from '@/shared/gameData'
import { asEncodedGiftId, asIdentityId, buildSaveablePlanner } from '@/test-utils/fixtures'
import { useIdentityMaxLevel } from '../../hooks/useMirrorDungeonSeason'
import { isMDPlanner } from '../../types/PlannerTypes'
import { PLANNER_CONTENT_KEYS } from './plannerContentKeys'

vi.mock('../../hooks/useMirrorDungeonSeason', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../hooks/useMirrorDungeonSeason')>()),
  useIdentityMaxLevel: vi.fn(() => 45),
}))

const MD_VERSION = 7

const ENCODED_9001 = asEncodedGiftId('9001')
const ENCODED_9005 = asEncodedGiftId('9005')
const ENCODED_9006 = asEncodedGiftId('9006')
const IDENTITY_10101 = asIdentityId('10101')
const IDENTITY_X = asIdentityId('11298')

describe('usePlannerStore', () => {
  describe('initial state', () => {
    it('starts with default equipment, skill EA, floor selections, and empty cold fields', () => {
      const store = createPlannerStore(MD_VERSION, 60)
      const s = store.getState()

      expect(s.equipment).toEqual(createDefaultEquipment(60))
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
      const store = createPlannerStore(MD_VERSION, 60, {
        title: 'Preset',
        category: '15F',
        isPublished: true,
      })
      const s = store.getState()

      expect(s.title).toBe('Preset')
      expect(s.category).toBe('15F')
      expect(s.isPublished).toBe(true)
      expect(s.floorSelections.map((f) => f.difficulty)).toEqual([
        ...Array.from({ length: 10 }, () => DUNGEON_IDX.HARD),
        ...Array.from({ length: 5 }, () => DUNGEON_IDX.EXTREME),
      ])
    })
  })

  describe('equipment actions', () => {
    it('setEquipment replaces the whole equipment map', () => {
      const store = createPlannerStore(MD_VERSION, 60)
      const replacement: Record<string, SinnerEquipment> = {
        '1': { identity: { id: IDENTITY_10101, uptie: 4, level: 50 }, egos: {} },
      }

      store.getState().setEquipment(replacement)

      expect(store.getState().equipment).toEqual(replacement)
    })

    it('setEquipment accepts an updater function', () => {
      const store = createPlannerStore(MD_VERSION, 60)

      store.getState().setEquipment((prev) => ({
        ...prev,
        '99': { identity: { id: IDENTITY_X, uptie: 4, level: 1 }, egos: {} },
      }))

      expect(store.getState().equipment['99']).toEqual({
        identity: { id: IDENTITY_X, uptie: 4, level: 1 },
        egos: {},
      })
    })
  })

  describe('floor selection actions', () => {
    it('updateFloorSelection replaces a single floor by index', () => {
      const store = createPlannerStore(MD_VERSION, 60)
      const updated = {
        themePackId: ThemePackIdSchema.parse('1001'),
        difficulty: DUNGEON_IDX.HARD,
        giftIds: new Set([ENCODED_9001]),
      }

      store.getState().updateFloorSelection(3, updated)

      const floors = store.getState().floorSelections
      expect(floors[3]).toEqual(updated)
      const firstFloor = floors[0]
      assert(firstFloor, 'the store holds no first floor')
      expect(firstFloor.themePackId).toBeNull()
    })
  })

  describe('deployment order action', () => {
    it('setDeploymentOrder replaces the array', () => {
      const store = createPlannerStore(MD_VERSION, 60)

      store.getState().setDeploymentOrder([2, 0, 1])

      expect(store.getState().deploymentOrder).toEqual([2, 0, 1])
    })
  })

  describe('warm + cold state setters', () => {
    it('setSelectedKeywords / setComprehensiveGiftIds store Set instances as-is', () => {
      const store = createPlannerStore(MD_VERSION, 60)
      const keywords = new Set(['k1', 'k2'])
      const gifts = new Set([ENCODED_9001])

      store.getState().setSelectedKeywords(keywords)
      store.getState().setComprehensiveGiftIds(gifts)

      expect(store.getState().selectedKeywords).toBe(keywords)
      expect(store.getState().comprehensiveGiftIds).toBe(gifts)
    })

    it('setSelectedGiftKeyword accepts null', () => {
      const store = createPlannerStore(MD_VERSION, 60)

      store.getState().setSelectedGiftKeyword('fire')
      expect(store.getState().selectedGiftKeyword).toBe('fire')

      store.getState().setSelectedGiftKeyword(null)
      expect(store.getState().selectedGiftKeyword).toBeNull()
    })

    it('setDeckVisibleCount accepts a value and an updater', () => {
      const store = createPlannerStore(MD_VERSION, 60)

      store.getState().setDeckVisibleCount(20)
      expect(store.getState().deckVisibleCount).toBe(20)

      store.getState().setDeckVisibleCount((prev) => prev + 5)
      expect(store.getState().deckVisibleCount).toBe(25)
    })

    it('a fresh planner switched to 10F defaults every floor to HARD', () => {
      const store = createPlannerStore(MD_VERSION, 60)

      store.getState().setCategory('10F')

      expect(
        store
          .getState()
          .floorSelections.slice(0, 10)
          .map((f) => f.difficulty),
      ).toEqual(Array.from({ length: 10 }, () => DUNGEON_IDX.HARD))
    })

    it('a category change keeps the difficulty of floors that hold a pack', () => {
      const store = createPlannerStore(MD_VERSION, 60)
      store.getState().updateFloorSelection(0, {
        themePackId: ThemePackIdSchema.parse('1001'),
        difficulty: DUNGEON_IDX.NORMAL,
        giftIds: new Set(),
      })

      store.getState().setCategory('10F')

      const floors = store.getState().floorSelections
      expect(floors.slice(0, 2).map((f) => f.difficulty)).toEqual([
        DUNGEON_IDX.NORMAL,
        DUNGEON_IDX.HARD,
      ])
    })

    it('setTitle / setCategory update the planner metadata', () => {
      const store = createPlannerStore(MD_VERSION, 60)

      store.getState().setTitle('My Plan')
      store.getState().setCategory('10F')

      const s = store.getState()
      expect(s.title).toBe('My Plan')
      expect(s.category).toBe('10F')
    })

    it('updateSinnerSkillEA replaces a single sinner skill entry', () => {
      const store = createPlannerStore(MD_VERSION, 60)
      const next: SkillEAState = { 0: 4, 1: 1, 2: 1 }

      store.getState().updateSinnerSkillEA('1', next)

      expect(store.getState().skillEAState['1']).toEqual(next)
    })

    it('updateSectionNote replaces a single section note', () => {
      const store = createPlannerStore(MD_VERSION, 60)
      const note = { content: { type: 'doc', content: [] } }

      store.getState().updateSectionNote('intro', note)

      expect(store.getState().sectionNotes.intro).toEqual(note)
    })
  })

  describe('initializeFromPlanner (array -> Set deserialization)', () => {
    function makeContent(overrides: Partial<MDPlannerContent> = {}): MDPlannerContent {
      return {
        type: 'MIRROR_DUNGEON',
        selectedKeywords: ['Combustion'],
        selectedBuffIds: [3],
        selectedGiftKeyword: 'fire',
        selectedGiftIds: ['g1', 'g2'],
        observationGiftIds: ['o1'],
        comprehensiveGiftIds: ['c1'],
        equipment: createDefaultEquipment(60),
        deploymentOrder: [0, 1, 2],
        skillEAState: createDefaultSkillEAState(),
        floorSelections: [
          {
            themePackId: ThemePackIdSchema.parse('1001'),
            difficulty: DUNGEON_IDX.NORMAL,
            giftIds: ['fg1', 'fg2'],
          },
        ],
        sectionNotes: { intro: { content: 'note' } },
        ...overrides,
      } as unknown as MDPlannerContent
    }

    it('converts serialized arrays into Set instances', () => {
      const store = createPlannerStore(MD_VERSION, 60)

      store
        .getState()
        .initializeFromPlanner(makeContent(), { title: 'T', category: '5F', isPublished: true })

      const s = store.getState()
      expect(s.selectedKeywords).toBeInstanceOf(Set)
      expect(Array.from(s.selectedKeywords)).toEqual(['Combustion'])
      expect(Array.from(s.selectedBuffIds)).toEqual([3])
      expect(Array.from(s.selectedGiftIds)).toEqual(['g1', 'g2'])
      expect(Array.from(s.observationGiftIds)).toEqual(['o1'])
      expect(Array.from(s.comprehensiveGiftIds)).toEqual(['c1'])
      const firstFloor = s.floorSelections[0]
      assert(firstFloor, 'the store holds no first floor')
      expect(firstFloor.giftIds).toBeInstanceOf(Set)
      expect(Array.from(firstFloor.giftIds)).toEqual(['fg1', 'fg2'])
      expect(s.title).toBe('T')
      expect(s.isPublished).toBe(true)
    })

    it('falls back to defaults when array fields are missing or non-arrays', () => {
      const store = createPlannerStore(MD_VERSION, 60)

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
      expect(s.equipment).toEqual(createDefaultEquipment(60))
    })

    it('backfills missing section-note keys from defaults and overrides supplied keys', () => {
      const store = createPlannerStore(MD_VERSION, 60)
      const defaultEmpty = createEmptyNoteContent()

      store.getState().initializeFromPlanner(
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
      const store = createPlannerStore(MD_VERSION, 60)
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
      const store = createPlannerStore(MD_VERSION, 60)
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
      const store = createPlannerStore(MD_VERSION, 60)
      store.getState().updateFloorSelection(2, {
        themePackId: ThemePackIdSchema.parse('1002'),
        difficulty: DUNGEON_IDX.HARD,
        giftIds: new Set([ENCODED_9005, ENCODED_9006]),
      })

      const planner = store.getState().getPlannerState()

      const thirdFloor = planner.floorSelections[2]
      assert(thirdFloor, 'the planner state holds no third floor')
      expect(thirdFloor.giftIds).toBeInstanceOf(Set)
      expect(Array.from(thirdFloor.giftIds)).toEqual(['9005', '9006'])
    })
  })

  describe('useStoreOwnedGiftIds', () => {
    it('agrees with the owned gifts derived from the saved content', () => {
      const content = {
        selectedKeywords: [],
        selectedBuffIds: [],
        selectedGiftKeyword: null,
        selectedGiftIds: [asEncodedGiftId('9001'), asEncodedGiftId('9002')],
        observationGiftIds: [asEncodedGiftId('9003')],
        comprehensiveGiftIds: [asEncodedGiftId('9004'), ENCODED_9005],
        equipment: createDefaultEquipment(60),
        deploymentOrder: [0, 1],
        skillEAState: createDefaultSkillEAState(),
        floorSelections: Array.from({ length: 15 }, (_, i) => ({
          themePackId: ThemePackIdSchema.parse(String(1001 + i)),
          difficulty: DUNGEON_IDX.NORMAL,
          giftIds: [asEncodedGiftId(String(9100 + i))],
        })),
        sectionNotes: {},
      } as unknown as MDPlannerContent
      const metadata = { title: 'Owned', category: '5F' as const, isPublished: false }
      const wrapper = ({ children }: { children: ReactNode }) => (
        <PlannerStoreProvider
          mdVersion={MD_VERSION}
          initialState={(maxLevel) => hydrateEditorState(content, metadata, maxLevel)}
        >
          {children}
        </PlannerStoreProvider>
      )

      const { result } = renderHook(() => useStoreOwnedGiftIds(), { wrapper })

      const expected = collectOwnedGiftIds(
        deserializeSets(content, metadata.category),
        floorCount(metadata.category),
      )
      expect(result.current).toEqual(expected)
      expect(result.current.size).toBe(10)
    })
  })

  describe('PlannerStoreProvider season', () => {
    it('resolves the season once and hands it to the tree', () => {
      vi.mocked(useIdentityMaxLevel).mockClear()
      const wrapper = ({ children }: { children: ReactNode }) => (
        <PlannerStoreProvider mdVersion={MD_VERSION}>{children}</PlannerStoreProvider>
      )

      const { result } = renderHook(() => useStoreSeason(), { wrapper })

      expect(result.current).toEqual({ contentVersion: MD_VERSION, identityMaxLevel: 45 })
      expect(useIdentityMaxLevel).toHaveBeenCalledOnce()
      expect(useIdentityMaxLevel).toHaveBeenCalledWith(MD_VERSION)
    })
  })

  describe('getPlannerState projection', () => {
    it('carries exactly the content keys a saved planner holds', () => {
      const planner = buildSaveablePlanner()
      assert(isMDPlanner(planner), 'the fixture is not a mirror dungeon planner')
      const store = createPlannerStore(MD_VERSION, 60)
      store.getState().initializeFromPlanner(planner.content, {
        title: planner.metadata.title,
        category: planner.config.category,
        isPublished: false,
      })

      const projected = store.getState().getPlannerState()

      expect(Object.keys(projected).sort()).toEqual(
        PLANNER_CONTENT_KEYS.filter((key) => key !== 'isPublished').sort(),
      )
    })
  })
})
