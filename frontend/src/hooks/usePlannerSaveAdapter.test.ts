/**
 * usePlannerSaveAdapter.test.ts
 *
 * Tests for the IndexedDB-only planner save adapter.
 * Verifies that operations correctly delegate to usePlannerStorage.
 */

import { renderHook } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import type { SaveablePlanner, PlannerSummary } from '@/types/PlannerTypes'
import type { SaveResult, StorageOperationOptions } from './usePlannerStorage'

// Mock usePlannerStorage
const mockSavePlanner = vi.fn<[SaveablePlanner, StorageOperationOptions?], Promise<SaveResult>>()
const mockListPlanners = vi.fn<[], Promise<PlannerSummary[]>>()
const mockLoadPlanner = vi.fn()
const mockDeletePlanner = vi.fn()
const mockGetOrCreateDeviceId = vi.fn()
const mockClearCorruptedPlanner = vi.fn()

vi.mock('./usePlannerStorage', () => ({
  usePlannerStorage: () => ({
    savePlanner: mockSavePlanner,
    listPlanners: mockListPlanners,
    loadPlanner: mockLoadPlanner,
    deletePlanner: mockDeletePlanner,
    getOrCreateDeviceId: mockGetOrCreateDeviceId,
    clearCorruptedPlanner: mockClearCorruptedPlanner,
  }),
}))

// Import after mocking
import { usePlannerSaveAdapter } from './usePlannerSaveAdapter'

/**
 * Create a minimal mock SaveablePlanner for testing
 */
function createMockPlanner(overrides: Partial<SaveablePlanner> = {}): SaveablePlanner {
  return {
    metadata: {
      id: 'test-uuid-123',
      status: 'draft',
      schemaVersion: 1,
      contentVersion: 6,
      plannerType: 'MIRROR_DUNGEON',
      syncVersion: 1,
      createdAt: '2024-01-01T00:00:00.000Z',
      lastModifiedAt: '2024-01-01T00:00:00.000Z',
      savedAt: null,
      userId: null,
      deviceId: 'device-123',
      ...overrides.metadata,
    },
    config: {
      type: 'MIRROR_DUNGEON',
      category: 'MD6_NORMAL_5F',
      ...overrides.config,
    },
    content: {
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
      ...overrides.content,
    },
  } as SaveablePlanner
}

describe('usePlannerSaveAdapter', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  describe('saveToLocal', () => {
    it('calls storage.savePlanner with planner data', async () => {
      // Arrange
      const mockPlanner = createMockPlanner()
      mockSavePlanner.mockResolvedValue({ success: true })

      const { result } = renderHook(() => usePlannerSaveAdapter())

      // Act
      const saveResult = await result.current.saveToLocal(mockPlanner)

      // Assert
      expect(mockSavePlanner).toHaveBeenCalledTimes(1)
      expect(mockSavePlanner).toHaveBeenCalledWith(mockPlanner, undefined)
      expect(saveResult.success).toBe(true)
    })

    it('passes options to storage.savePlanner', async () => {
      // Arrange
      const mockPlanner = createMockPlanner()
      const mockOnError = vi.fn()
      const options: StorageOperationOptions = { onError: mockOnError }
      mockSavePlanner.mockResolvedValue({ success: true })

      const { result } = renderHook(() => usePlannerSaveAdapter())

      // Act
      await result.current.saveToLocal(mockPlanner, options)

      // Assert
      expect(mockSavePlanner).toHaveBeenCalledWith(mockPlanner, options)
    })

    it('returns error result when save fails', async () => {
      // Arrange
      const mockPlanner = createMockPlanner()
      mockSavePlanner.mockResolvedValue({ success: false, errorCode: 'quotaExceeded' })

      const { result } = renderHook(() => usePlannerSaveAdapter())

      // Act
      const saveResult = await result.current.saveToLocal(mockPlanner)

      // Assert
      expect(saveResult.success).toBe(false)
      expect(saveResult.errorCode).toBe('quotaExceeded')
    })
  })

  describe('listLocal', () => {
    it('returns planners from storage.listPlanners', async () => {
      // Arrange
      const mockSummaries: PlannerSummary[] = [
        {
          id: 'planner-1',
          title: 'First Planner',
          plannerType: 'MIRROR_DUNGEON',
          category: 'MD6_NORMAL_5F',
          status: 'saved',
          lastModifiedAt: '2024-01-02T00:00:00.000Z',
          savedAt: '2024-01-02T00:00:00.000Z',
        },
        {
          id: 'planner-2',
          title: 'Second Planner',
          plannerType: 'MIRROR_DUNGEON',
          category: 'MD6_NORMAL_10F',
          status: 'draft',
          lastModifiedAt: '2024-01-01T00:00:00.000Z',
          savedAt: null,
        },
      ]
      mockListPlanners.mockResolvedValue(mockSummaries)

      const { result } = renderHook(() => usePlannerSaveAdapter())

      // Act
      const listResult = await result.current.listLocal()

      // Assert
      expect(mockListPlanners).toHaveBeenCalledTimes(1)
      expect(listResult).toEqual(mockSummaries)
      expect(listResult).toHaveLength(2)
    })

    it('returns empty array when no planners exist', async () => {
      // Arrange
      mockListPlanners.mockResolvedValue([])

      const { result } = renderHook(() => usePlannerSaveAdapter())

      // Act
      const listResult = await result.current.listLocal()

      // Assert
      expect(listResult).toEqual([])
    })
  })

  describe('loadFromLocal', () => {
    it('calls storage.loadPlanner with id', async () => {
      // Arrange
      const mockPlanner = createMockPlanner()
      mockLoadPlanner.mockResolvedValue(mockPlanner)

      const { result } = renderHook(() => usePlannerSaveAdapter())

      // Act
      const loadResult = await result.current.loadFromLocal('test-uuid-123')

      // Assert
      expect(mockLoadPlanner).toHaveBeenCalledWith('test-uuid-123', undefined)
      expect(loadResult).toEqual(mockPlanner)
    })

    it('returns null when planner not found', async () => {
      // Arrange
      mockLoadPlanner.mockResolvedValue(null)

      const { result } = renderHook(() => usePlannerSaveAdapter())

      // Act
      const loadResult = await result.current.loadFromLocal('non-existent')

      // Assert
      expect(loadResult).toBeNull()
    })
  })

  describe('deleteFromLocal', () => {
    it('calls storage.deletePlanner with id', async () => {
      // Arrange
      mockDeletePlanner.mockResolvedValue(undefined)

      const { result } = renderHook(() => usePlannerSaveAdapter())

      // Act
      await result.current.deleteFromLocal('test-uuid-123')

      // Assert
      expect(mockDeletePlanner).toHaveBeenCalledWith('test-uuid-123')
    })
  })

  describe('getOrCreateDeviceId', () => {
    it('delegates to storage.getOrCreateDeviceId', async () => {
      // Arrange
      mockGetOrCreateDeviceId.mockResolvedValue('device-uuid-456')

      const { result } = renderHook(() => usePlannerSaveAdapter())

      // Act
      const deviceId = await result.current.getOrCreateDeviceId()

      // Assert
      expect(mockGetOrCreateDeviceId).toHaveBeenCalledTimes(1)
      expect(deviceId).toBe('device-uuid-456')
    })
  })
})
