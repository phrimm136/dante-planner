/**
 * usePlannerSyncAdapter.test.ts
 *
 * Tests for the server API planner sync adapter.
 * Verifies that operations correctly delegate to plannerApi.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook } from '@testing-library/react'
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
      id: '550e8400-e29b-41d4-a716-446655440000',
      title: 'Test Planner',
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
      category: '5F',
    },
    content: {
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
    id: '550e8400-e29b-41d4-a716-446655440000' as PlannerId,
    userId: 456,
    title: 'Test Planner',
    category: '5F',
    status: 'saved',
    content: JSON.stringify({
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
    it('uses upsert for new planner with syncVersion=1', async () => {
      // Arrange
      const mockPlanner = createMockPlanner({ syncVersion: 1, userId: null })
      const mockResponse = createMockServerResponse()
      mockUpsert.mockResolvedValue(mockResponse)

      const { result: hookResult } = renderHook(() => usePlannerSyncAdapter())
      const adapter = hookResult.current

      // Act
      const result = await adapter.syncToServer(mockPlanner)

      // Assert
      expect(mockUpsert).toHaveBeenCalledTimes(1)
      expect(mockUpsert).toHaveBeenCalledWith(
        '550e8400-e29b-41d4-a716-446655440000',
        expect.objectContaining({
          category: '5F',
          title: 'Test Planner',
          content: expect.any(String),
          contentVersion: 6,
          plannerType: 'MIRROR_DUNGEON',
        }),
        undefined
      )
      expect(result.metadata.syncVersion).toBe(2)
    })

    it('uses upsert for existing planner when userId exists', async () => {
      // Arrange
      const mockPlanner = createMockPlanner({ syncVersion: 5, userId: '456' })
      const mockResponse = createMockServerResponse({ syncVersion: 6 })
      mockUpsert.mockResolvedValue(mockResponse)

      const { result: hookResult } = renderHook(() => usePlannerSyncAdapter())
      const adapter = hookResult.current

      // Act
      const result = await adapter.syncToServer(mockPlanner)

      // Assert
      expect(mockUpsert).toHaveBeenCalledTimes(1)
      expect(mockUpsert).toHaveBeenCalledWith(
        '550e8400-e29b-41d4-a716-446655440000',
        expect.objectContaining({
          title: 'Test Planner',
          syncVersion: 5,
        }),
        undefined
      )
      expect(result.metadata.syncVersion).toBe(6)
    })

    it('passes force=true to upsert when specified', async () => {
      // Arrange
      const mockPlanner = createMockPlanner({ syncVersion: 3, userId: '456' })
      const mockResponse = createMockServerResponse({ syncVersion: 4 })
      mockUpsert.mockResolvedValue(mockResponse)

      const { result: hookResult } = renderHook(() => usePlannerSyncAdapter())
      const adapter = hookResult.current

      // Act
      await adapter.syncToServer(mockPlanner, true)

      // Assert
      expect(mockUpsert).toHaveBeenCalledWith(
        '550e8400-e29b-41d4-a716-446655440000',
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

      const { result: hookResult } = renderHook(() => usePlannerSyncAdapter())
      const adapter = hookResult.current

      // Act & Assert
      await expect(adapter.syncToServer(mockPlanner)).rejects.toThrow(
        'Server sync only supports MIRROR_DUNGEON planners'
      )
      expect(mockUpsert).not.toHaveBeenCalled()
    })
  })

  describe('listFromServer', () => {
    it('converts server summaries to local format', async () => {
      // Arrange
      const mockServerSummaries: ServerPlannerSummary[] = [
        {
          id: '550e8400-e29b-41d4-a716-446655440001' as PlannerId,
          title: 'First Planner',
          plannerType: 'MIRROR_DUNGEON',
          category: '5F',
          status: 'saved',
          syncVersion: 3,
          lastModifiedAt: '2024-01-02T00:00:00.000Z',
        },
        {
          id: '550e8400-e29b-41d4-a716-446655440002' as PlannerId,
          title: 'Second Planner',
          plannerType: 'MIRROR_DUNGEON',
          category: '10F',
          status: 'draft',
          syncVersion: 1,
          lastModifiedAt: '2024-01-01T00:00:00.000Z',
        },
      ]
      mockListAll.mockResolvedValue(mockServerSummaries)

      const { result: hookResult } = renderHook(() => usePlannerSyncAdapter())
      const adapter = hookResult.current

      // Act
      const result = await adapter.listFromServer()

      // Assert
      expect(mockListAll).toHaveBeenCalledTimes(1)
      expect(result).toHaveLength(2)
      expect(result[0]).toEqual({
        id: '550e8400-e29b-41d4-a716-446655440001',
        title: 'First Planner',
        plannerType: 'MIRROR_DUNGEON',
        category: '5F',
        status: 'saved',
        lastModifiedAt: '2024-01-02T00:00:00.000Z',
        savedAt: null,
        syncVersion: 3,
      })
    })

    it('returns empty array when no server planners', async () => {
      // Arrange
      mockListAll.mockResolvedValue([])

      const { result: hookResult } = renderHook(() => usePlannerSyncAdapter())
      const adapter = hookResult.current

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

      const { result: hookResult } = renderHook(() => usePlannerSyncAdapter())
      const adapter = hookResult.current

      // Act
      const result = await adapter.fetchFromServer('550e8400-e29b-41d4-a716-446655440000')

      // Assert
      expect(mockGet).toHaveBeenCalledWith('550e8400-e29b-41d4-a716-446655440000')
      expect(result).not.toBeNull()
      expect(result?.metadata.id).toBe('550e8400-e29b-41d4-a716-446655440000')
      expect(result?.metadata.syncVersion).toBe(2)
    })

    it('returns null when planner not found', async () => {
      // Arrange
      mockGet.mockRejectedValue(new Error('Not found'))

      const { result: hookResult } = renderHook(() => usePlannerSyncAdapter())
      const adapter = hookResult.current

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

      const { result: hookResult } = renderHook(() => usePlannerSyncAdapter())
      const adapter = hookResult.current

      // Act
      await adapter.deleteFromServer('550e8400-e29b-41d4-a716-446655440000')

      // Assert
      expect(mockDelete).toHaveBeenCalledWith('550e8400-e29b-41d4-a716-446655440000')
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

      const { result: hookResult } = renderHook(() => usePlannerSyncAdapter())
      const adapter = hookResult.current

      // Act
      await adapter.syncToServer(mockPlanner)

      // Assert
      expect(mockUpsert).toHaveBeenCalledWith(
        '550e8400-e29b-41d4-a716-446655440000',
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

      const { result: hookResult } = renderHook(() => usePlannerSyncAdapter())
      const adapter = hookResult.current

      // Act
      await adapter.syncToServer(mockPlanner)

      // Assert
      expect(mockUpsert).toHaveBeenCalledWith(
        '550e8400-e29b-41d4-a716-446655440000',
        expect.objectContaining({
          selectedKeywords: [],
        }),
        undefined
      )
    })
  })
})
