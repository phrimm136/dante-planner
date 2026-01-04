import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { usePlannerSave, type PlannerState } from './usePlannerSave'
import { ConflictError } from '@/lib/api'
import { DUNGEON_IDX } from '@/lib/constants'

// Mock dependencies
vi.mock('./usePlannerStorageAdapter', () => ({
  usePlannerStorageAdapter: vi.fn(() => ({
    isAuthenticated: true,
    savePlanner: vi.fn().mockResolvedValue({
      metadata: { syncVersion: 2 },
    }),
    loadPlanner: vi.fn().mockResolvedValue(null),
  })),
}))

vi.mock('./usePlannerStorage', () => ({
  usePlannerStorage: vi.fn(() => ({
    getOrCreateDeviceId: vi.fn().mockResolvedValue('test-device-id'),
    setCurrentDraftId: vi.fn().mockResolvedValue(undefined),
    enforceGuestDraftLimit: vi.fn().mockResolvedValue(undefined),
  })),
}))

// Create a minimal valid PlannerState for testing
function createTestState(overrides: Partial<PlannerState> = {}): PlannerState {
  return {
    title: 'Test Planner',
    category: '5F',
    selectedKeywords: new Set<string>(),
    selectedBuffIds: new Set<number>(),
    selectedGiftKeyword: null,
    selectedGiftIds: new Set<string>(),
    observationGiftIds: new Set<string>(),
    comprehensiveGiftIds: new Set<string>(),
    equipment: {},
    deploymentOrder: [],
    skillEAState: {},
    floorSelections: Array.from({ length: 15 }, () => ({
      themePackId: null,
      difficulty: DUNGEON_IDX.NORMAL,
      giftIds: new Set<string>(),
    })),
    sectionNotes: {},
    ...overrides,
  }
}

describe('usePlannerSave', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  it('should initialize with correct default values', () => {
    const state = createTestState()
    const { result } = renderHook(() =>
      usePlannerSave({
        state,
        schemaVersion: 1,
        contentVersion: 6,
        plannerType: 'MIRROR_DUNGEON',
      })
    )

    expect(result.current.isAutoSaving).toBe(false)
    expect(result.current.isSaving).toBe(false)
    expect(result.current.errorCode).toBe(null)
    expect(result.current.conflictState).toBe(null)
    expect(result.current.plannerId).toBeDefined()
    expect(typeof result.current.save).toBe('function')
    expect(typeof result.current.resolveConflict).toBe('function')
  })

  it('should not auto-save on initial render', async () => {
    const { usePlannerStorageAdapter } = await import('./usePlannerStorageAdapter')
    const mockSavePlanner = vi.fn().mockResolvedValue({ metadata: { syncVersion: 2 } })
    vi.mocked(usePlannerStorageAdapter).mockReturnValue({
      isAuthenticated: true,
      savePlanner: mockSavePlanner,
      loadPlanner: vi.fn(),
      deletePlanner: vi.fn(),
      listPlanners: vi.fn(),
    })

    const state = createTestState()
    renderHook(() =>
      usePlannerSave({
        state,
        schemaVersion: 1,
        contentVersion: 6,
        plannerType: 'MIRROR_DUNGEON',
      })
    )

    // Fast-forward past debounce time
    await act(async () => {
      vi.advanceTimersByTime(3000)
    })

    // Should not have saved on initial render
    expect(mockSavePlanner).not.toHaveBeenCalled()
  })

  it('should auto-save after state change and debounce', async () => {
    const { usePlannerStorageAdapter } = await import('./usePlannerStorageAdapter')
    const mockSavePlanner = vi.fn().mockResolvedValue({ metadata: { syncVersion: 2 } })
    vi.mocked(usePlannerStorageAdapter).mockReturnValue({
      isAuthenticated: true,
      savePlanner: mockSavePlanner,
      loadPlanner: vi.fn(),
      deletePlanner: vi.fn(),
      listPlanners: vi.fn(),
    })

    const initialState = createTestState({ title: 'Initial' })
    const { rerender } = renderHook(
      ({ state }) =>
        usePlannerSave({
          state,
          schemaVersion: 1,
          contentVersion: 6,
          plannerType: 'MIRROR_DUNGEON',
        }),
      { initialProps: { state: initialState } }
    )

    // Change state
    const updatedState = createTestState({ title: 'Updated' })
    rerender({ state: updatedState })

    // Before debounce - should not save
    expect(mockSavePlanner).not.toHaveBeenCalled()

    // Fast-forward past debounce time (2000ms)
    await act(async () => {
      vi.advanceTimersByTime(2500)
    })

    // Should have auto-saved
    expect(mockSavePlanner).toHaveBeenCalled()
  })

  it('should set errorCode to conflict when ConflictError is thrown', async () => {
    const { usePlannerStorageAdapter } = await import('./usePlannerStorageAdapter')
    const mockSavePlanner = vi.fn().mockRejectedValue(new ConflictError('Conflict', 5))
    vi.mocked(usePlannerStorageAdapter).mockReturnValue({
      isAuthenticated: true,
      savePlanner: mockSavePlanner,
      loadPlanner: vi.fn(),
      deletePlanner: vi.fn(),
      listPlanners: vi.fn(),
    })

    const state = createTestState()
    const { result } = renderHook(() =>
      usePlannerSave({
        state,
        schemaVersion: 1,
        contentVersion: 6,
        plannerType: 'MIRROR_DUNGEON',
      })
    )

    // Trigger manual save
    await act(async () => {
      await result.current.save()
    })

    expect(result.current.errorCode).toBe('conflict')
    expect(result.current.conflictState).toEqual({
      serverVersion: 5,
      detectedAt: expect.any(String),
    })
  })

  it('should set errorCode to saveFailed for other errors', async () => {
    const { usePlannerStorageAdapter } = await import('./usePlannerStorageAdapter')
    const mockSavePlanner = vi.fn().mockRejectedValue(new Error('Network error'))
    vi.mocked(usePlannerStorageAdapter).mockReturnValue({
      isAuthenticated: true,
      savePlanner: mockSavePlanner,
      loadPlanner: vi.fn(),
      deletePlanner: vi.fn(),
      listPlanners: vi.fn(),
    })

    const state = createTestState()
    const { result } = renderHook(() =>
      usePlannerSave({
        state,
        schemaVersion: 1,
        contentVersion: 6,
        plannerType: 'MIRROR_DUNGEON',
      })
    )

    // Trigger manual save
    await act(async () => {
      await result.current.save()
    })

    expect(result.current.errorCode).toBe('saveFailed')
  })

  it('should clear error when clearError is called', async () => {
    const { usePlannerStorageAdapter } = await import('./usePlannerStorageAdapter')
    const mockSavePlanner = vi.fn().mockRejectedValue(new Error('Network error'))
    vi.mocked(usePlannerStorageAdapter).mockReturnValue({
      isAuthenticated: true,
      savePlanner: mockSavePlanner,
      loadPlanner: vi.fn(),
      deletePlanner: vi.fn(),
      listPlanners: vi.fn(),
    })

    const state = createTestState()
    const { result } = renderHook(() =>
      usePlannerSave({
        state,
        schemaVersion: 1,
        contentVersion: 6,
        plannerType: 'MIRROR_DUNGEON',
      })
    )

    // Trigger save to create error
    await act(async () => {
      await result.current.save()
    })

    expect(result.current.errorCode).toBe('saveFailed')

    // Clear error
    act(() => {
      result.current.clearError()
    })

    expect(result.current.errorCode).toBe(null)
  })

  it('should use tracked syncVersion for manual save', async () => {
    const { usePlannerStorageAdapter } = await import('./usePlannerStorageAdapter')
    const mockSavePlanner = vi.fn().mockResolvedValue({ metadata: { syncVersion: 3 } })
    vi.mocked(usePlannerStorageAdapter).mockReturnValue({
      isAuthenticated: true,
      savePlanner: mockSavePlanner,
      loadPlanner: vi.fn(),
      deletePlanner: vi.fn(),
      listPlanners: vi.fn(),
    })

    const state = createTestState()
    const { result } = renderHook(() =>
      usePlannerSave({
        state,
        schemaVersion: 1,
        contentVersion: 6,
        plannerType: 'MIRROR_DUNGEON',
        initialSyncVersion: 2,
      })
    )

    // Trigger manual save
    await act(async () => {
      await result.current.save()
    })

    // Verify savePlanner was called with the tracked syncVersion (2)
    // This is the critical fix - manual save should use tracked version, not hardcoded 1
    expect(mockSavePlanner).toHaveBeenCalledWith(
      expect.objectContaining({
        metadata: expect.objectContaining({
          syncVersion: 2,
        }),
      })
    )
  })

  describe('conflict resolution', () => {
    it('should send serverVersion+1 when resolving with overwrite', async () => {
      const { usePlannerStorageAdapter } = await import('./usePlannerStorageAdapter')
      // First call fails with conflict (serverVersion: 5), second call succeeds
      const mockSavePlanner = vi
        .fn()
        .mockRejectedValueOnce(new ConflictError('Conflict', 5))
        .mockResolvedValueOnce({ metadata: { syncVersion: 6 } })

      vi.mocked(usePlannerStorageAdapter).mockReturnValue({
        isAuthenticated: true,
        savePlanner: mockSavePlanner,
        loadPlanner: vi.fn(),
        deletePlanner: vi.fn(),
        listPlanners: vi.fn(),
      })

      const state = createTestState()
      const { result } = renderHook(() =>
        usePlannerSave({
          state,
          schemaVersion: 1,
          contentVersion: 6,
          plannerType: 'MIRROR_DUNGEON',
          initialSyncVersion: 2,
        })
      )

      // First save fails with conflict
      await act(async () => {
        await result.current.save()
      })

      expect(result.current.errorCode).toBe('conflict')
      expect(result.current.conflictState?.serverVersion).toBe(5)

      // Resolve with overwrite
      await act(async () => {
        await result.current.resolveConflict('overwrite')
      })

      // Should have called savePlanner with serverVersion + 1 = 6
      expect(mockSavePlanner).toHaveBeenLastCalledWith(
        expect.objectContaining({
          metadata: expect.objectContaining({
            syncVersion: 6, // serverVersion (5) + 1
          }),
        })
      )

      // Conflict should be cleared
      expect(result.current.errorCode).toBe(null)
      expect(result.current.conflictState).toBe(null)
    })

    it('should call loadPlanner and onServerReload when resolving with discard', async () => {
      const { usePlannerStorageAdapter } = await import('./usePlannerStorageAdapter')
      const mockLoadPlanner = vi.fn().mockResolvedValue({
        metadata: { id: 'test-id', syncVersion: 5 },
        content: { title: 'Server Version' },
      })
      const mockSavePlanner = vi
        .fn()
        .mockRejectedValueOnce(new ConflictError('Conflict', 5))

      vi.mocked(usePlannerStorageAdapter).mockReturnValue({
        isAuthenticated: true,
        savePlanner: mockSavePlanner,
        loadPlanner: mockLoadPlanner,
        deletePlanner: vi.fn(),
        listPlanners: vi.fn(),
      })

      const mockOnServerReload = vi.fn()
      const state = createTestState()
      const { result } = renderHook(() =>
        usePlannerSave({
          state,
          schemaVersion: 1,
          contentVersion: 6,
          plannerType: 'MIRROR_DUNGEON',
          onServerReload: mockOnServerReload,
        })
      )

      // First save fails with conflict
      await act(async () => {
        await result.current.save()
      })

      expect(result.current.errorCode).toBe('conflict')

      // Resolve with discard
      await act(async () => {
        await result.current.resolveConflict('discard')
      })

      // Should have called loadPlanner
      expect(mockLoadPlanner).toHaveBeenCalled()

      // Should have called onServerReload with server data
      expect(mockOnServerReload).toHaveBeenCalledWith(
        expect.objectContaining({
          metadata: expect.objectContaining({ syncVersion: 5 }),
        })
      )

      // Conflict should be cleared
      expect(result.current.errorCode).toBe(null)
      expect(result.current.conflictState).toBe(null)
    })

    it('should restore syncVersion if overwrite fails (H2 fix)', async () => {
      const { usePlannerStorageAdapter } = await import('./usePlannerStorageAdapter')
      // First call fails with conflict (serverVersion: 5)
      // Second call (overwrite attempt) also fails
      const mockSavePlanner = vi
        .fn()
        .mockRejectedValueOnce(new ConflictError('Conflict', 5))
        .mockRejectedValueOnce(new Error('Network error'))

      vi.mocked(usePlannerStorageAdapter).mockReturnValue({
        isAuthenticated: true,
        savePlanner: mockSavePlanner,
        loadPlanner: vi.fn(),
        deletePlanner: vi.fn(),
        listPlanners: vi.fn(),
      })

      const state = createTestState()
      const { result } = renderHook(() =>
        usePlannerSave({
          state,
          schemaVersion: 1,
          contentVersion: 6,
          plannerType: 'MIRROR_DUNGEON',
          initialSyncVersion: 2,
        })
      )

      // First save fails with conflict
      await act(async () => {
        await result.current.save()
      })

      expect(result.current.errorCode).toBe('conflict')

      // Try to resolve with overwrite, but it fails
      await act(async () => {
        await result.current.resolveConflict('overwrite')
      })

      // Error should be updated (not conflict anymore, but saveFailed)
      expect(result.current.errorCode).toBe('saveFailed')

      // The syncVersion should be restored to original (2), not stuck at 6
      // This is verified by the next save attempt using the original version
      mockSavePlanner.mockResolvedValueOnce({ metadata: { syncVersion: 3 } })

      await act(async () => {
        result.current.clearError()
        await result.current.save()
      })

      // Should use original syncVersion (2), not the failed attempt version (6)
      expect(mockSavePlanner).toHaveBeenLastCalledWith(
        expect.objectContaining({
          metadata: expect.objectContaining({
            syncVersion: 2,
          }),
        })
      )
    })
  })
})
