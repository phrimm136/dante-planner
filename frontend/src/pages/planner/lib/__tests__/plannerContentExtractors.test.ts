/**
 * plannerContentExtractors.test.ts
 *
 * Unit tests for planner content extraction and filter matching.
 * Pure function tests — no React rendering needed.
 */

import { describe, it, expect } from 'vitest'
import type { MDPlannerContent, SaveablePlanner } from '../../types/PlannerTypes'
import type { PlannerSearchFilters } from '../../types/PlannerSearchTypes'
import { EMPTY_PLANNER_SEARCH_FILTERS } from '../../types/PlannerSearchTypes'
import {
  extractIdentityIds,
  extractEgoIds,
  extractGiftIds,
  extractThemePackIds,
  matchesPlannerFilters,
} from '../plannerContentExtractors'

// ============================================================================
// Helpers
// ============================================================================

/**
 * Create a minimal valid MDPlannerContent with optional overrides
 */
function createMockMDContent(
  overrides: Partial<MDPlannerContent> = {},
): MDPlannerContent {
  return {
    selectedKeywords: [],
    selectedBuffIds: [],
    selectedGiftKeyword: null,
    selectedGiftIds: [],
    observationGiftIds: [],
    comprehensiveGiftIds: [],
    equipment: {},
    deploymentOrder: [],
    skillEAState: {},
    floorSelections: [],
    sectionNotes: {},
    ...overrides,
  }
}

/**
 * Create a minimal SaveablePlanner (MD type) for filter matching tests
 */
function createMockPlanner(
  contentOverrides: Partial<MDPlannerContent> = {},
  metadataOverrides: Partial<SaveablePlanner['metadata']> = {},
): SaveablePlanner {
  return {
    metadata: {
      id: 'test-id',
      title: 'Test Planner',
      status: 'draft',
      schemaVersion: 1,
      contentVersion: 6,
      plannerType: 'MIRROR_DUNGEON',
      syncVersion: 1,
      createdAt: '2024-01-01T00:00:00.000Z',
      lastModifiedAt: '2024-01-01T00:00:00.000Z',
      savedAt: null,
      deviceId: 'test-device',
      ...metadataOverrides,
    },
    config: { type: 'MIRROR_DUNGEON', category: '5F' },
    content: createMockMDContent(contentOverrides),
  }
}

/**
 * Create filters with overrides on top of empty defaults
 */
function createFilters(
  overrides: Partial<PlannerSearchFilters> = {},
): PlannerSearchFilters {
  return { ...EMPTY_PLANNER_SEARCH_FILTERS, ...overrides }
}

// ============================================================================
// extractIdentityIds
// ============================================================================

describe('extractIdentityIds', () => {
  it('extracts identity IDs from equipment map with multiple sinners', () => {
    const content = createMockMDContent({
      equipment: {
        '1': { identity: { id: '10101', uptie: 4, level: 40 }, egos: {} },
        '2': { identity: { id: '10201', uptie: 3, level: 35 }, egos: {} },
      },
    })

    const result = extractIdentityIds(content)

    expect(result).toEqual(new Set(['10101', '10201']))
  })

  it('returns empty set when equipment is empty', () => {
    const content = createMockMDContent({ equipment: {} })
    expect(extractIdentityIds(content)).toEqual(new Set())
  })

  it('returns empty set when equipment is undefined', () => {
    const content = createMockMDContent()
    // Force undefined to test the guard
    ;(content as Record<string, unknown>).equipment = undefined
    expect(extractIdentityIds(content)).toEqual(new Set())
  })

  it('handles equipment entries with missing identity field', () => {
    const content = createMockMDContent({
      equipment: {
        '1': { identity: { id: '10101', uptie: 4, level: 40 }, egos: {} },
        '2': {} as never, // missing identity
      },
    })

    const result = extractIdentityIds(content)

    expect(result).toEqual(new Set(['10101']))
  })
})

// ============================================================================
// extractEgoIds
// ============================================================================

describe('extractEgoIds', () => {
  it('extracts EGO IDs from multiple sinners and ego types', () => {
    const content = createMockMDContent({
      equipment: {
        '1': {
          identity: { id: '10101', uptie: 4, level: 40 },
          egos: {
            ZAYIN: { id: '20101', threadspin: 1 },
            HE: { id: '20102', threadspin: 2 },
          },
        },
        '3': {
          identity: { id: '10301', uptie: 3, level: 30 },
          egos: {
            TETH: { id: '20301', threadspin: 1 },
          },
        },
      },
    })

    const result = extractEgoIds(content)

    expect(result).toEqual(new Set(['20101', '20102', '20301']))
  })

  it('returns empty set when no egos equipped', () => {
    const content = createMockMDContent({
      equipment: {
        '1': { identity: { id: '10101', uptie: 4, level: 40 }, egos: {} },
      },
    })

    expect(extractEgoIds(content)).toEqual(new Set())
  })

  it('handles equipment entries with empty egos object', () => {
    const content = createMockMDContent({
      equipment: {
        '1': {
          identity: { id: '10101', uptie: 4, level: 40 },
          egos: {
            ALEPH: { id: '20501', threadspin: 4 },
          },
        },
        '2': {
          identity: { id: '10201', uptie: 3, level: 35 },
          egos: {},
        },
      },
    })

    const result = extractEgoIds(content)

    expect(result).toEqual(new Set(['20501']))
  })
})

// ============================================================================
// extractGiftIds
// ============================================================================

describe('extractGiftIds', () => {
  it('extracts from all 4 sources', () => {
    const content = createMockMDContent({
      selectedGiftIds: ['g1', 'g2'],
      observationGiftIds: ['g3'],
      comprehensiveGiftIds: ['g4'],
      floorSelections: [
        { themePackId: null, difficulty: 0, giftIds: ['g5', 'g6'] },
      ],
    })

    const result = extractGiftIds(content)

    expect(result).toEqual(new Set(['g1', 'g2', 'g3', 'g4', 'g5', 'g6']))
  })

  it('deduplicates across sources', () => {
    const content = createMockMDContent({
      selectedGiftIds: ['g1', 'g2'],
      observationGiftIds: ['g2', 'g3'],
      comprehensiveGiftIds: [],
      floorSelections: [
        { themePackId: null, difficulty: 0, giftIds: ['g1'] },
      ],
    })

    const result = extractGiftIds(content)

    expect(result).toEqual(new Set(['g1', 'g2', 'g3']))
    expect(result.size).toBe(3)
  })

  it('returns empty set when all sources are empty', () => {
    const content = createMockMDContent({
      selectedGiftIds: [],
      observationGiftIds: [],
      comprehensiveGiftIds: [],
      floorSelections: [],
    })

    expect(extractGiftIds(content)).toEqual(new Set())
  })

  it('handles undefined sources gracefully', () => {
    const content = createMockMDContent()
    ;(content as Record<string, unknown>).selectedGiftIds = undefined
    ;(content as Record<string, unknown>).observationGiftIds = undefined
    ;(content as Record<string, unknown>).comprehensiveGiftIds = undefined
    ;(content as Record<string, unknown>).floorSelections = undefined

    expect(extractGiftIds(content)).toEqual(new Set())
  })
})

// ============================================================================
// extractThemePackIds
// ============================================================================

describe('extractThemePackIds', () => {
  it('extracts non-null themePackId from floorSelections', () => {
    const content = createMockMDContent({
      floorSelections: [
        { themePackId: '1001', difficulty: 0, giftIds: [] },
        { themePackId: '1002', difficulty: 1, giftIds: [] },
      ],
    })

    const result = extractThemePackIds(content)

    expect(result).toEqual(new Set(['1001', '1002']))
  })

  it('skips null themePackId entries', () => {
    const content = createMockMDContent({
      floorSelections: [
        { themePackId: '1001', difficulty: 0, giftIds: [] },
        { themePackId: null, difficulty: 0, giftIds: [] },
        { themePackId: '1003', difficulty: 1, giftIds: [] },
      ],
    })

    const result = extractThemePackIds(content)

    expect(result).toEqual(new Set(['1001', '1003']))
    expect(result.size).toBe(2)
  })

  it('returns empty set when no floorSelections', () => {
    const content = createMockMDContent({ floorSelections: [] })
    expect(extractThemePackIds(content)).toEqual(new Set())
  })

  it('returns empty set when floorSelections is undefined', () => {
    const content = createMockMDContent()
    ;(content as Record<string, unknown>).floorSelections = undefined
    expect(extractThemePackIds(content)).toEqual(new Set())
  })
})

// ============================================================================
// matchesPlannerFilters
// ============================================================================

describe('matchesPlannerFilters', () => {
  it('returns true when no filters are active', () => {
    const plan = createMockPlanner()
    const filters = createFilters()

    expect(matchesPlannerFilters(plan, filters)).toBe(true)
  })

  describe('title filter', () => {
    it('matches case-insensitive substring', () => {
      const plan = createMockPlanner({}, { title: 'My Burn Build' })
      const filters = createFilters({ title: 'burn' })

      expect(matchesPlannerFilters(plan, filters)).toBe(true)
    })

    it('rejects when title does not contain substring', () => {
      const plan = createMockPlanner({}, { title: 'My Slash Build' })
      const filters = createFilters({ title: 'burn' })

      expect(matchesPlannerFilters(plan, filters)).toBe(false)
    })
  })

  describe('keyword filter', () => {
    it('matches when plan has all selected keywords (AND semantics)', () => {
      const plan = createMockPlanner({
        selectedKeywords: ['Burn', 'Slash', 'Pierce'],
      })
      const filters = createFilters({ keywords: ['Burn', 'Slash'] })

      expect(matchesPlannerFilters(plan, filters)).toBe(true)
    })

    it('rejects when plan is missing a required keyword', () => {
      const plan = createMockPlanner({
        selectedKeywords: ['Burn'],
      })
      const filters = createFilters({ keywords: ['Burn', 'Slash'] })

      expect(matchesPlannerFilters(plan, filters)).toBe(false)
    })

    it('rejects when plan has no keywords', () => {
      const plan = createMockPlanner({ selectedKeywords: [] })
      const filters = createFilters({ keywords: ['Burn'] })

      expect(matchesPlannerFilters(plan, filters)).toBe(false)
    })
  })

  describe('identity filter', () => {
    it('matches when plan has all required identity IDs (AND semantics)', () => {
      const plan = createMockPlanner({
        equipment: {
          '1': { identity: { id: '10101', uptie: 4, level: 40 }, egos: {} },
          '2': { identity: { id: '10201', uptie: 3, level: 35 }, egos: {} },
        },
      })
      const filters = createFilters({ identityIds: ['10101', '10201'] })

      expect(matchesPlannerFilters(plan, filters)).toBe(true)
    })

    it('rejects when plan is missing a required identity', () => {
      const plan = createMockPlanner({
        equipment: {
          '1': { identity: { id: '10101', uptie: 4, level: 40 }, egos: {} },
        },
      })
      const filters = createFilters({ identityIds: ['10101', '10201'] })

      expect(matchesPlannerFilters(plan, filters)).toBe(false)
    })
  })

  describe('EGO filter', () => {
    it('matches when plan has all required EGO IDs (AND semantics)', () => {
      const plan = createMockPlanner({
        equipment: {
          '1': {
            identity: { id: '10101', uptie: 4, level: 40 },
            egos: {
              ZAYIN: { id: '20101', threadspin: 1 },
              HE: { id: '20102', threadspin: 2 },
            },
          },
        },
      })
      const filters = createFilters({ egoIds: ['20101', '20102'] })

      expect(matchesPlannerFilters(plan, filters)).toBe(true)
    })

    it('rejects when plan is missing a required EGO', () => {
      const plan = createMockPlanner({
        equipment: {
          '1': {
            identity: { id: '10101', uptie: 4, level: 40 },
            egos: { ZAYIN: { id: '20101', threadspin: 1 } },
          },
        },
      })
      const filters = createFilters({ egoIds: ['20101', '99999'] })

      expect(matchesPlannerFilters(plan, filters)).toBe(false)
    })
  })

  describe('multiple filter composition', () => {
    it('composes title + keyword + identity with AND', () => {
      const plan = createMockPlanner(
        {
          selectedKeywords: ['Burn'],
          equipment: {
            '1': { identity: { id: '10101', uptie: 4, level: 40 }, egos: {} },
          },
        },
        { title: 'My Burn Build' },
      )
      const filters = createFilters({
        title: 'burn',
        keywords: ['Burn'],
        identityIds: ['10101'],
      })

      expect(matchesPlannerFilters(plan, filters)).toBe(true)
    })

    it('fails when any one filter does not match', () => {
      const plan = createMockPlanner(
        {
          selectedKeywords: ['Burn'],
          equipment: {
            '1': { identity: { id: '10101', uptie: 4, level: 40 }, egos: {} },
          },
        },
        { title: 'My Burn Build' },
      )
      // Title matches, keywords match, but identity filter fails
      const filters = createFilters({
        title: 'burn',
        keywords: ['Burn'],
        identityIds: ['99999'],
      })

      expect(matchesPlannerFilters(plan, filters)).toBe(false)
    })
  })

  describe('non-MD planner', () => {
    it('content filters fail for non-MD planners', () => {
      const plan: SaveablePlanner = {
        metadata: {
          id: 'rr-id',
          title: 'RR Plan',
          status: 'draft',
          schemaVersion: 1,
          contentVersion: 5,
          plannerType: 'REFRACTED_RAILWAY',
          syncVersion: 1,
          createdAt: '2024-01-01T00:00:00.000Z',
          lastModifiedAt: '2024-01-01T00:00:00.000Z',
          savedAt: null,
          deviceId: 'test-device',
        },
        config: { type: 'REFRACTED_RAILWAY', category: 'RR5' },
        content: {},
      }
      const filters = createFilters({ keywords: ['Burn'] })

      expect(matchesPlannerFilters(plan, filters)).toBe(false)
    })

    it('title-only filter still works for non-MD planners', () => {
      const plan: SaveablePlanner = {
        metadata: {
          id: 'rr-id',
          title: 'Railway Build',
          status: 'draft',
          schemaVersion: 1,
          contentVersion: 5,
          plannerType: 'REFRACTED_RAILWAY',
          syncVersion: 1,
          createdAt: '2024-01-01T00:00:00.000Z',
          lastModifiedAt: '2024-01-01T00:00:00.000Z',
          savedAt: null,
          deviceId: 'test-device',
        },
        config: { type: 'REFRACTED_RAILWAY', category: 'RR5' },
        content: {},
      }
      const filters = createFilters({ title: 'railway' })

      expect(matchesPlannerFilters(plan, filters)).toBe(true)
    })
  })

  describe('missing content edge cases', () => {
    it('returns false when plan has no keywords but keyword filter is active', () => {
      const content = createMockMDContent()
      ;(content as Record<string, unknown>).selectedKeywords = undefined
      const plan = createMockPlanner()
      ;(plan.content as Record<string, unknown>).selectedKeywords = undefined
      const filters = createFilters({ keywords: ['Burn'] })

      expect(matchesPlannerFilters(plan, filters)).toBe(false)
    })
  })
})
