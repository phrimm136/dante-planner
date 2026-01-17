/**
 * usePlannerSyncAdapter.test.ts
 *
 * Tests for the server API planner sync adapter.
 * Verifies that operations correctly delegate to plannerApi.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import type {
  SaveablePlanner,
  ServerPlannerResponse,
  ServerPlannerSummary,
  PlannerId,
} from '@/types/PlannerTypes'

// Mock plannerApi
const mockCreate = vi.fn()
const mockUpdate = vi.fn()
const mockUpsert = vi.fn()
const mockGet = vi.fn()
const mockDelete = vi.fn()
const mockList = vi.fn()
const mockListAll = vi.fn()

vi.mock('@/lib/plannerApi', () => ({
  plannerApi: {
    create: (...args: unknown[]) => mockCreate(...args),
    update: (...args: unknown[]) => mockUpdate(...args),
    upsert: (...args: unknown[]) => mockUpsert(...args),
    get: (...args: unknown[]) => mockGet(...args),
    delete: (...args: unknown[]) => mockDelete(...args),
    list: (...args: unknown[]) => mockList(...args),
    listAll: (...args: unknown[]) => mockListAll(...args),
  },
}))

// Import after mocking
import { usePlannerSyncAdapter } from './usePlannerSyncAdapter'

/**
 * Create a minimal mock SaveablePlanner for testing
 */
function createMockPlanner(overrides: Partial<{
  syncVersion: number
  userId: string | null
  plannerType: 'MIRROR_DUNGEON' | 'REFRACTED_RAILWAY'
}> = {}): SaveablePlanner {
  return {
    metadata: {
      id: 'test-uuid-123',
      status: 'draft',
      schemaVersion: 1,
      contentVersion: 6,
      plannerType: overrides.plannerType ?? 'MIRROR_DUNGEON',
      syncVersion: overrides.syncVersion ?? 1,
      createdAt: '2024-01-01T00:00:00.000Z',
      lastModifiedAt: '2024-01-01T00:00:00.000Z',
      savedAt: null,
      userId: overrides.userId ?? null,
      deviceId: 'device-123',
    },
    config: {
      type: overrides.plannerType ?? 'MIRROR_DUNGEON',
      category: 'MD6_NORMAL_5F',
    },
    content: {
      type: overrides.plannerType ?? 'MIRROR_DUNGEON',
      title: 'Test Planner',
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
    },
  } as SaveablePlanner
}

/**
 * Create a mock server response
 */
function createMockServerResponse(overrides: Partial<ServerPlannerResponse> = {}): ServerPlannerResponse {
  return {
    id: 'test-uuid-123' as PlannerId,
    userId: 456,
    title: 'Test Planner',
    category: 'MD6_NORMAL_5F',
    status: 'saved',
    content: JSON.stringify({
      type: 'MIRROR_DUNGEON',
      title: 'Test Planner',
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
    }),
    schemaVersion: 1,
    contentVersion: 6,
    plannerType: 'MIRROR_DUNGEON',
    syncVersion: 2,
    createdAt: '2024-01-01T00:00:00.000Z',
    lastModifiedAt: '2024-01-01T00:00:00.000Z',
    published: false,
    ...overrides,
  }
}

describe('usePlannerSyncAdapter', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  describe('syncToServer', () => {
    it('creates new planner when syncVersion=1 and no userId', async () => {
      // Arrange
      const mockPlanner = createMockPlanner({ syncVersion: 1, userId: null })
      const mockResponse = createMockServerResponse()
      mockCreate.mockResolvedValue(mockResponse)

      const adapter = usePlannerSyncAdapter()

      // Act
      const result = await adapter.syncToServer(mockPlanner)

      // Assert
      expect(mockCreate).toHaveBeenCalledTimes(1)
      expect(mockCreate).toHaveBeenCalledWith(expect.objectContaining({
        category: 'MD6_NORMAL_5F',
        title: 'Test Planner',
        content: expect.any(String),
        contentVersion: 6,
        plannerType: 'MIRROR_DUNGEON',
      }))
      expect(mockUpdate).not.toHaveBeenCalled()
      expect(result.metadata.syncVersion).toBe(2)
    })

    it('updates existing planner when userId exists', async () => {
      // Arrange
      const mockPlanner = createMockPlanner({ syncVersion: 5, userId: '456' })
      const mockResponse = createMockServerResponse({ syncVersion: 6 })
      mockUpdate.mockResolvedValue(mockResponse)

      const adapter = usePlannerSyncAdapter()

      // Act
      const result = await adapter.syncToServer(mockPlanner)

      // Assert
      expect(mockUpdate).toHaveBeenCalledTimes(1)
      expect(mockUpdate).toHaveBeenCalledWith(
        'test-uuid-123',
        expect.objectContaining({
          title: 'Test Planner',
          syncVersion: 5,
        }),
        undefined // force not passed
      )
      expect(mockCreate).not.toHaveBeenCalled()
      expect(result.metadata.syncVersion).toBe(6)
    })

    it('passes force=true to update when specified', async () => {
      // Arrange
      const mockPlanner = createMockPlanner({ syncVersion: 3, userId: '456' })
      const mockResponse = createMockServerResponse({ syncVersion: 4 })
      mockUpdate.mockResolvedValue(mockResponse)

      const adapter = usePlannerSyncAdapter()

      // Act
      await adapter.syncToServer(mockPlanner, true)

      // Assert
      expect(mockUpdate).toHaveBeenCalledWith(
        'test-uuid-123',
        expect.any(Object),
        true // force=true passed
      )
    })

    it('throws error for non-MIRROR_DUNGEON new planners', async () => {
      // Arrange
      const mockPlanner = createMockPlanner({
        syncVersion: 1,
        userId: null,
        plannerType: 'REFRACTED_RAILWAY',
      })

      const adapter = usePlannerSyncAdapter()

      // Act & Assert
      await expect(adapter.syncToServer(mockPlanner)).rejects.toThrow(
        'Server sync only supports MIRROR_DUNGEON planners'
      )
      expect(mockCreate).not.toHaveBeenCalled()
    })
  })

  describe('listFromServer', () => {
    it('converts server summaries to local format', async () => {
      // Arrange
      const mockServerSummaries: ServerPlannerSummary[] = [
        {
          id: 'planner-1' as PlannerId,
          title: 'First Planner',
          category: 'MD6_NORMAL_5F',
          status: 'saved',
          syncVersion: 3,
          lastModifiedAt: '2024-01-02T00:00:00.000Z',
        },
        {
          id: 'planner-2' as PlannerId,
          title: 'Second Planner',
          category: 'MD6_NORMAL_10F',
          status: 'draft',
          syncVersion: 1,
          lastModifiedAt: '2024-01-01T00:00:00.000Z',
        },
      ]
      mockListAll.mockResolvedValue(mockServerSummaries)

      const adapter = usePlannerSyncAdapter()

      // Act
      const result = await adapter.listFromServer()

      // Assert
      expect(mockListAll).toHaveBeenCalledTimes(1)
      expect(result).toHaveLength(2)
      expect(result[0]).toEqual({
        id: 'planner-1',
        title: 'First Planner',
        plannerType: 'MIRROR_DUNGEON', // Default value applied
        category: 'MD6_NORMAL_5F',
        status: 'saved',
        lastModifiedAt: '2024-01-02T00:00:00.000Z',
        savedAt: null, // Server summary doesn't include savedAt
      })
    })

    it('returns empty array when no server planners', async () => {
      // Arrange
      mockListAll.mockResolvedValue([])

      const adapter = usePlannerSyncAdapter()

      // Act
      const result = await adapter.listFromServer()

      // Assert
      expect(result).toEqual([])
    })
  })

  describe('fetchFromServer', () => {
    it('returns SaveablePlanner when found', async () => {
      // Arrange
      const mockResponse = createMockServerResponse()
      mockGet.mockResolvedValue(mockResponse)

      const adapter = usePlannerSyncAdapter()

      // Act
      const result = await adapter.fetchFromServer('test-uuid-123')

      // Assert
      expect(mockGet).toHaveBeenCalledWith('test-uuid-123')
      expect(result).not.toBeNull()
      expect(result?.metadata.id).toBe('test-uuid-123')
      expect(result?.metadata.syncVersion).toBe(2)
    })

    it('returns null when planner not found', async () => {
      // Arrange
      mockGet.mockRejectedValue(new Error('Not found'))

      const adapter = usePlannerSyncAdapter()

      // Act
      const result = await adapter.fetchFromServer('non-existent')

      // Assert
      expect(result).toBeNull()
    })
  })

  describe('deleteFromServer', () => {
    it('calls plannerApi.delete with id', async () => {
      // Arrange
      mockDelete.mockResolvedValue(undefined)

      const adapter = usePlannerSyncAdapter()

      // Act
      await adapter.deleteFromServer('test-uuid-123')

      // Assert
      expect(mockDelete).toHaveBeenCalledWith('test-uuid-123')
    })
  })

  describe('syncToServer keyword extraction', () => {
    it('extracts selectedKeywords from content and includes in request', async () => {
      // Arrange
      const mockPlanner = createMockPlanner({ syncVersion: 1, userId: null })
      // Add keywords to content
      mockPlanner.content = {
        ...mockPlanner.content,
        selectedKeywords: ['Burn', 'Slash', 'Pierce'],
      } as typeof mockPlanner.content
      const mockResponse = createMockServerResponse()
      mockUpsert.mockResolvedValue(mockResponse)

      const adapter = usePlannerSyncAdapter()

      // Act
      await adapter.syncToServer(mockPlanner)

      // Assert
      expect(mockUpsert).toHaveBeenCalledWith(
        'test-uuid-123',
        expect.objectContaining({
          selectedKeywords: ['Burn', 'Slash', 'Pierce'],
        }),
        undefined
      )
    })

    it('sends empty array when no keywords selected', async () => {
      // Arrange
      const mockPlanner = createMockPlanner({ syncVersion: 1, userId: null })
      mockPlanner.content = {
        ...mockPlanner.content,
        selectedKeywords: [],
      } as typeof mockPlanner.content
      const mockResponse = createMockServerResponse()
      mockUpsert.mockResolvedValue(mockResponse)

      const adapter = usePlannerSyncAdapter()

      // Act
      await adapter.syncToServer(mockPlanner)

      // Assert
      expect(mockUpsert).toHaveBeenCalledWith(
        'test-uuid-123',
        expect.objectContaining({
          selectedKeywords: [],
        }),
        undefined
      )
    })
  })
})
