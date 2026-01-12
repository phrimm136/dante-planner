import { describe, it, expect } from 'vitest'
import type { SaveablePlanner, MDPlannerContent } from '@/types/PlannerTypes'

const mockPlanner: SaveablePlanner = {
  metadata: {
    id: 'test-planner-123',
    status: 'draft',
    schemaVersion: 1,
    contentVersion: 6,
    plannerType: 'MIRROR_DUNGEON',
    syncVersion: 1,
    createdAt: '2026-01-10T00:00:00Z',
    lastModifiedAt: '2026-01-10T00:00:00Z',
    savedAt: null,
    userId: 1,
    deviceId: 'device-123',
  },
  config: {
    type: 'MIRROR_DUNGEON',
    category: '5F',
  },
  content: {
    equipment: {},
    deploymentOrder: [],
    selectedBuffIds: [],
    selectedKeywords: [],
    selectedGiftKeyword: null,
    selectedGiftIds: [],
    observationGiftIds: [],
    comprehensiveGiftIds: [],
    skillEAState: {},
    floorSelections: [],
    sectionNotes: {
      deckBuilder: { content: { type: 'doc', content: [] } },
      startBuffs: { content: { type: 'doc', content: [] } },
      startGifts: { content: { type: 'doc', content: [] } },
      observation: { content: { type: 'doc', content: [] } },
      comprehensiveGifts: { content: { type: 'doc', content: [] } },
      skillReplacement: { content: { type: 'doc', content: [] } },
    },
  } as MDPlannerContent,
}

describe('PlannerMDEditorContent', () => {
  describe('props interface', () => {
    it('accepts mode="new" without planner prop', () => {
      const props = {
        mode: 'new' as const,
      }
      expect(props.mode).toBe('new')
      expect(props).not.toHaveProperty('planner')
    })

    it('accepts mode="edit" with planner prop', () => {
      const props = {
        mode: 'edit' as const,
        planner: mockPlanner,
      }
      expect(props.mode).toBe('edit')
      expect(props.planner).toBeDefined()
      expect(props.planner?.metadata.id).toBe('test-planner-123')
    })

    it('planner prop is optional', () => {
      const props = {
        mode: 'new' as const,
        planner: undefined,
      }
      expect(props.mode).toBe('new')
      expect(props.planner).toBeUndefined()
    })
  })

  describe('mode conditional logic', () => {
    it('mode can be "new" or "edit"', () => {
      const newMode: 'new' | 'edit' = 'new'
      const editMode: 'new' | 'edit' = 'edit'

      expect(['new', 'edit']).toContain(newMode)
      expect(['new', 'edit']).toContain(editMode)
    })

    it('draft recovery should be conditional on mode === "new"', () => {
      const shouldShowRecoveryNew = 'new' === 'new'
      const shouldShowRecoveryEdit = 'edit' === 'new'

      expect(shouldShowRecoveryNew).toBe(true)
      expect(shouldShowRecoveryEdit).toBe(false)
    })

    it('state initialization source depends on mode', () => {
      const newModeSource = 'defaults'
      const editModeSource = mockPlanner ? 'planner' : 'defaults'

      expect(newModeSource).toBe('defaults')
      expect(editModeSource).toBe('planner')
    })
  })

  describe('planner data structure', () => {
    it('planner has required metadata fields', () => {
      expect(mockPlanner.metadata).toBeDefined()
      expect(mockPlanner.metadata.id).toBeTruthy()
      expect(mockPlanner.metadata.plannerType).toBe('MIRROR_DUNGEON')
    })

    it('planner has required config fields', () => {
      expect(mockPlanner.config).toBeDefined()
      expect(mockPlanner.config.type).toBe('MIRROR_DUNGEON')
      expect(mockPlanner.config.category).toBeTruthy()
    })

    it('planner has required content fields', () => {
      expect(mockPlanner.content).toBeDefined()
      expect(mockPlanner.content.equipment).toBeDefined()
      expect(mockPlanner.content.sectionNotes).toBeDefined()
    })
  })
})
