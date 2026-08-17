/**
 * useMDUserPlannersData.test.tsx
 *
 * Tests for user (personal) planners data hook.
 * Uses Vitest for testing query key structure.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import type { PlannerSummary, SaveablePlanner } from '../../types/PlannerTypes'
import { BATCH_PULL_MAX_IDS } from '@/lib/constants'
import { categorizePlanner } from '../../lib/syncPlan'

const syncMocks = vi.hoisted(() => ({
  isAuthenticated: true,
  syncEnabled: true,
  listFromServer: vi.fn(async (): Promise<unknown[]> => []),
  listLocal: vi.fn(async (): Promise<unknown[]> => []),
  getOrCreateDeviceId: vi.fn(async (): Promise<unknown> => ({ ok: true, value: 'test-device' })),
  saveToLocal: vi.fn(async (_planner: unknown): Promise<unknown> => ({ ok: true })),
  loadFromLocal: vi.fn(async (_id: string): Promise<unknown> => null),
  fetchFromServer: vi.fn(async (_id: string): Promise<unknown> => null),
  syncToServer: vi.fn(async (_planner: unknown, _force?: boolean): Promise<unknown> => null),
  /** Null stands for a gift spec that has not loaded, which now fails closed. */
  egoGiftSpec: {} as unknown,
  /** Stands in for the chunked pull: one yield per request. */
  batchChunks: vi.fn(async function* (ids: string[]): AsyncGenerator<unknown[]> {
    yield ids.map((id) => ({ id }))
  }),
}))

vi.mock('../../lib/plannerApi', () => ({
  plannerApi: { batchChunks: (ids: string[]) => syncMocks.batchChunks(ids) },
}))

// Both real hooks return a fresh object literal per render, so the mocks do too:
// dependency-array stability must not rest on their identity.
vi.mock('../usePlannerStorage', () => ({
  usePlannerStorage: () => ({
    getOrCreateDeviceId: syncMocks.getOrCreateDeviceId,
    saveToLocal: syncMocks.saveToLocal,
    loadFromLocal: syncMocks.loadFromLocal,
    listLocal: syncMocks.listLocal,
    listLocalFull: vi.fn(async () => []),
    deleteFromLocal: vi.fn(async () => ({ ok: true })),
    clearCorruptedLocal: vi.fn(async () => undefined),
  }),
}))

vi.mock('../usePlannerSyncAdapter', () => ({
  usePlannerSyncAdapter: () => ({
    syncToServer: syncMocks.syncToServer,
    fetchFromServer: syncMocks.fetchFromServer,
    deleteFromServer: vi.fn(),
    listFromServer: syncMocks.listFromServer,
  }),
  serverResponseToSaveable: (response: { id: string }) => ({ metadata: { id: response.id } }),
  acknowledgedCopy: ({
    planner,
    ack,
  }: {
    planner: SaveablePlanner
    ack: { syncVersion: number }
  }) => ({
    ...planner,
    metadata: { ...planner.metadata, syncVersion: ack.syncVersion },
  }),
}))

vi.mock('@/shared/auth', () => ({
  useAuthQuery: () => ({ data: syncMocks.isAuthenticated ? { id: 'user-1' } : null }),
}))

vi.mock('@/shared/userSettings', () => ({
  useUserSettingsQuery: () => ({ data: { syncEnabled: syncMocks.syncEnabled } }),
}))

vi.mock('@/pages/egoGift', () => ({
  useEGOGiftListData: () => ({ spec: syncMocks.egoGiftSpec, i18n: {} }),
}))

// The validators have their own suites; what this one pins is the wiring around
// them — which planner is validated, and what a refusal does to the batch.
vi.mock('../../lib/plannerValidation', () => ({
  validatePlannerForDraftSave: () => null,
  validatePlannerForPublish: () => ({ isValid: true, errors: [] }),
}))

import { userPlannersQueryKeys, useMDUserPlannersData } from '../useMDUserPlannersData'
import type { ConflictOutcome } from '../../lib/conflictChoice'

describe('useMDUserPlannersData background sync', () => {
  function createWrapper() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return function Wrapper({ children }: { children: React.ReactNode }) {
      return (
        <QueryClientProvider client={queryClient}>
          <React.Suspense fallback={null}>{children}</React.Suspense>
        </QueryClientProvider>
      )
    }
  }

  beforeEach(() => {
    syncMocks.isAuthenticated = true
    syncMocks.syncEnabled = true
    syncMocks.listFromServer.mockClear()
    syncMocks.listLocal.mockClear()
    syncMocks.saveToLocal.mockClear()
    syncMocks.batchChunks.mockClear()
    syncMocks.loadFromLocal.mockClear()
    syncMocks.fetchFromServer.mockClear()
    syncMocks.syncToServer.mockClear()
    syncMocks.listFromServer.mockResolvedValue([])
    syncMocks.listLocal.mockResolvedValue([])
    syncMocks.loadFromLocal.mockResolvedValue(null)
    syncMocks.fetchFromServer.mockResolvedValue(null)
    syncMocks.batchChunks.mockImplementation(async function* (ids: string[]) {
      yield ids.map((id) => ({ id }))
    })
  })

  it('pulls from the server once and stays at once across re-renders', async () => {
    const { result, rerender } = renderHook(
      (props: { page: number }) => useMDUserPlannersData(props),
      {
        wrapper: createWrapper(),
        initialProps: { page: 0 },
      },
    )

    await waitFor(() => expect(result.current).not.toBeNull())
    await waitFor(() => expect(syncMocks.listFromServer).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(result.current.isSyncing).toBe(false))

    // Every re-render hands the hook a new options object and new storage/adapter
    // identities; none of that may re-enter the sync.
    for (let i = 0; i < 5; i++) rerender({ page: 0 })

    await waitFor(() => expect(result.current.isSyncing).toBe(false))
    expect(syncMocks.listFromServer).toHaveBeenCalledTimes(1)
  })

  it('does not re-pull when auth or sync toggles back to the already-synced state', async () => {
    const { result, rerender } = renderHook(
      (props: { page: number }) => useMDUserPlannersData(props),
      {
        wrapper: createWrapper(),
        initialProps: { page: 0 },
      },
    )

    await waitFor(() => expect(syncMocks.listFromServer).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(result.current.isSyncing).toBe(false))

    syncMocks.syncEnabled = false
    rerender({ page: 0 })
    syncMocks.syncEnabled = true
    rerender({ page: 0 })

    await waitFor(() => expect(result.current.isSyncing).toBe(false))
    expect(syncMocks.listFromServer).toHaveBeenCalledTimes(1)
  })

  it('never reaches the server for a guest', async () => {
    syncMocks.isAuthenticated = false

    const { result, rerender } = renderHook(
      (props: { page: number }) => useMDUserPlannersData(props),
      {
        wrapper: createWrapper(),
        initialProps: { page: 0 },
      },
    )

    await waitFor(() => expect(result.current).not.toBeNull())
    for (let i = 0; i < 3; i++) rerender({ page: 0 })

    expect(syncMocks.listFromServer).not.toHaveBeenCalled()
    expect(result.current.isAuthenticated).toBe(false)
  })

  /** Server rows with no local counterpart, which categorizeSync sends to the pull residue. */
  function serverRows(count: number): unknown[] {
    return Array.from({ length: count }, (_, i) => ({
      id: `planner-${i}`,
      title: `Planner ${i}`,
      plannerType: 'MIRROR_DUNGEON',
      category: '5F',
      status: 'saved',
      lastModifiedAt: '2026-01-01T00:00:00.000Z',
      savedAt: '2026-01-01T00:00:00.000Z',
      syncVersion: 1,
    }))
  }

  async function runSyncWith(rows: unknown[]) {
    syncMocks.listFromServer.mockResolvedValue(rows)
    const { result } = renderHook((props: { page: number }) => useMDUserPlannersData(props), {
      wrapper: createWrapper(),
      initialProps: { page: 0 },
    })
    await waitFor(() => expect(result.current).not.toBeNull())
    await waitFor(() => expect(result.current.isSyncing).toBe(false))
    return result
  }

  /** The planner ids handed to storage, in the order they were written. */
  function savedIds(): string[] {
    return syncMocks.saveToLocal.mock.calls.map((call) => (call[0] as SaveablePlanner).metadata.id)
  }

  it('pulls a residue of three rows in exactly one batch request', async () => {
    await runSyncWith(serverRows(3))

    expect(syncMocks.batchChunks).toHaveBeenCalledTimes(1)
    expect(syncMocks.batchChunks).toHaveBeenCalledWith(['planner-0', 'planner-1', 'planner-2'])
  })

  it('hands the whole residue to one call, leaving chunking to the api client', async () => {
    await runSyncWith(serverRows(BATCH_PULL_MAX_IDS + 1))

    expect(syncMocks.batchChunks).toHaveBeenCalledTimes(1)
    const [firstCall] = syncMocks.batchChunks.mock.calls
    expect(firstCall?.[0]).toHaveLength(BATCH_PULL_MAX_IDS + 1)
  })

  it('issues no request for an empty residue, which the server would reject', async () => {
    await runSyncWith([])

    expect(syncMocks.batchChunks).not.toHaveBeenCalled()
  })

  it('leaves rows the response omits local-untouched', async () => {
    // The response is not positionally aligned: the middle id resolves to nothing.
    syncMocks.batchChunks.mockImplementation(async function* (ids: string[]) {
      yield ids.filter((id) => id !== 'planner-1').map((id) => ({ id }))
    })

    await runSyncWith(serverRows(3))

    expect(syncMocks.saveToLocal).toHaveBeenCalledTimes(2)
    expect(savedIds()).toEqual(['planner-0', 'planner-2'])
  })

  it('keeps the rows earlier chunks delivered when a later chunk fails', async () => {
    syncMocks.batchChunks.mockImplementation(async function* (ids: string[]) {
      yield ids.slice(0, 2).map((id) => ({ id }))
      throw new Error('second chunk failed')
    })

    await runSyncWith(serverRows(3))

    // The failure aborts the remaining chunks without discarding what already landed.
    expect(savedIds()).toEqual(['planner-0', 'planner-1'])
  })

  /**
   * Drive a draft-vs-server conflict into pendingConflicts, then resolve it by
   * keeping the local copy (choice 'overwrite' plans a single keepLocal effect).
   */
  async function resolveKeepingLocal() {
    const conflicted = {
      id: 'planner-0',
      title: 'Planner 0',
      plannerType: 'MIRROR_DUNGEON',
      category: '5F',
      status: 'draft',
      lastModifiedAt: '2026-01-01T00:00:00.000Z',
      savedAt: null,
      syncVersion: 1,
    }
    const localPlanner = {
      metadata: { ...conflicted, syncVersion: 1 },
      config: { type: 'MIRROR_DUNGEON', category: '5F' },
      content: {},
    }
    syncMocks.listFromServer.mockResolvedValue([{ ...conflicted, status: 'saved', syncVersion: 5 }])
    syncMocks.listLocal.mockResolvedValue([conflicted])
    syncMocks.loadFromLocal.mockResolvedValue({ ok: true, value: localPlanner })
    syncMocks.fetchFromServer.mockResolvedValue({
      ok: true,
      value: { planner: localPlanner, ack: { syncVersion: 5 } },
    })
    // The server echoes the status it was sent — still 'draft'.
    syncMocks.syncToServer.mockResolvedValue({
      planner: localPlanner,
      ack: { syncVersion: 6 },
    })

    const { result } = renderHook((props: { page: number }) => useMDUserPlannersData(props), {
      wrapper: createWrapper(),
      initialProps: { page: 0 },
    })
    await waitFor(() => expect(result.current).not.toBeNull())
    await waitFor(() => expect(result.current.pendingConflicts).toHaveLength(1))

    await act(async () => {
      await result.current.resolveConflicts([{ id: 'planner-0', choice: 'overwrite' }])
    })

    const stored = syncMocks.saveToLocal.mock.calls.at(-1)?.[0] as SaveablePlanner
    return stored
  }

  it('marks the kept local planner saved with a savedAt timestamp', async () => {
    const stored = await resolveKeepingLocal()

    expect(stored.metadata.status).toBe('saved')
    expect(stored.metadata.savedAt).not.toBeNull()
    expect(stored.metadata.syncVersion).toBe(6)
  })

  function makeSummary(overrides: Partial<PlannerSummary>): PlannerSummary {
    return {
      id: 'planner-0',
      title: 'Planner 0',
      plannerType: 'MIRROR_DUNGEON',
      category: '5F',
      status: 'saved',
      lastModifiedAt: '2026-01-01T00:00:00.000Z',
      savedAt: '2026-01-01T00:00:00.000Z',
      syncVersion: 1,
      ...overrides,
    }
  }

  it('leaves the resolved row pullable, not conflicting, on the next server bump', () => {
    // Left a draft, the row would prompt the same conflict again on every bump.
    const resolved = makeSummary({ status: 'saved', syncVersion: 6 })
    const bumped = makeSummary({ status: 'saved', syncVersion: 7 })

    expect(categorizePlanner(resolved, bumped)).toBe('pull')
    expect(categorizePlanner({ ...resolved, status: 'draft' }, bumped)).toBe('conflict')
  })
})

describe('userPlannersQueryKeys', () => {
  it('creates consistent base key', () => {
    const key = userPlannersQueryKeys.all
    expect(key).toEqual(['userPlanners'])
  })

  it('creates unique keys for different auth states', () => {
    const guestKey = userPlannersQueryKeys.list(false)
    const authKey = userPlannersQueryKeys.list(true)

    expect(guestKey).not.toEqual(authKey)
    expect(guestKey[2]).toEqual({ isAuthenticated: false })
    expect(authKey[2]).toEqual({ isAuthenticated: true })
  })

  it('includes auth state in list key for cache separation', () => {
    const key = userPlannersQueryKeys.list(true)

    expect(key[0]).toBe('userPlanners')
    expect(key[1]).toBe('list')
    expect(key[2]).toHaveProperty('isAuthenticated', true)
  })
})

describe('useMDUserPlannersData window-focus policy', () => {
  it('opts both local-storage queries out of the global focus refetch', async () => {
    syncMocks.isAuthenticated = true
    syncMocks.syncEnabled = true
    // The client carries the app-wide default, so the assertions below prove an
    // override rather than the absence of a setting.
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, refetchOnWindowFocus: true } },
    })
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={null}>{children}</React.Suspense>
      </QueryClientProvider>
    )

    const { result } = renderHook((props: { page: number }) => useMDUserPlannersData(props), {
      wrapper,
      initialProps: { page: 0 },
    })
    await waitFor(() => expect(result.current).not.toBeNull())

    // IndexedDB is the source of truth here, so a focus event carries no news
    // about it; only server-backed planner queries take the global default.
    const focusOptionFor = (queryKey: readonly unknown[]) =>
      queryClient.getQueryCache().find({ queryKey })?.observers[0]?.options.refetchOnWindowFocus

    expect(focusOptionFor(userPlannersQueryKeys.list(true))).toBe(false)
    expect(focusOptionFor(userPlannersQueryKeys.listFull(true))).toBe(false)
  })
})

describe('useMDUserPlannersData batch conflict resolution', () => {
  function createWrapper() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return function Wrapper({ children }: { children: React.ReactNode }) {
      return (
        <QueryClientProvider client={queryClient}>
          <React.Suspense fallback={null}>{children}</React.Suspense>
        </QueryClientProvider>
      )
    }
  }

  beforeEach(() => {
    syncMocks.isAuthenticated = true
    syncMocks.syncEnabled = true
    syncMocks.egoGiftSpec = {}
    syncMocks.getOrCreateDeviceId.mockResolvedValue({ ok: true, value: 'test-device' })
    syncMocks.saveToLocal.mockClear()
    syncMocks.syncToServer.mockClear()
    syncMocks.fetchFromServer.mockClear()
    syncMocks.listLocal.mockResolvedValue([])
  })

  /** Three drafts the server has moved past, which categorizeSync sends to conflict. */
  function conflictRows() {
    return ['planner-0', 'planner-1', 'planner-2'].map((id) => ({
      id,
      title: id,
      plannerType: 'MIRROR_DUNGEON',
      category: '5F',
      status: 'draft',
      lastModifiedAt: '2026-01-01T00:00:00.000Z',
      savedAt: null,
      syncVersion: 1,
    }))
  }

  async function pendingThreeConflicts() {
    const rows = conflictRows()
    syncMocks.listFromServer.mockResolvedValue(
      rows.map((row) => ({ ...row, status: 'saved', syncVersion: 5 })),
    )
    syncMocks.listLocal.mockResolvedValue(rows)
    syncMocks.loadFromLocal.mockImplementation(async (id: string) => ({
      ok: true,
      value: {
        metadata: { ...rows.find((row) => row.id === id)!, syncVersion: 1 },
        config: { type: 'MIRROR_DUNGEON', category: '5F' },
        content: {},
      },
    }))
    syncMocks.fetchFromServer.mockImplementation(async (id: string) => ({
      ok: true,
      value: {
        planner: {
          metadata: { ...rows.find((row) => row.id === id)!, syncVersion: 5 },
          config: { type: 'MIRROR_DUNGEON', category: '5F' },
          content: {},
        },
        ack: { syncVersion: 5 },
      },
    }))

    const { result } = renderHook((props: { page: number }) => useMDUserPlannersData(props), {
      wrapper: createWrapper(),
      initialProps: { page: 0 },
    })
    await waitFor(() => expect(result.current).not.toBeNull())
    await waitFor(() => expect(result.current.pendingConflicts).toHaveLength(3))
    return result
  }

  it('stops at the first failure and keeps every id it did not resolve', async () => {
    const result = await pendingThreeConflicts()
    // The second upload is rejected; the third resolution must never be attempted.
    syncMocks.syncToServer.mockImplementation(async (planner: unknown) => {
      const id = (planner as SaveablePlanner).metadata.id
      if (id === 'planner-1') throw new Error('server rejected the write')
      return { planner, ack: { syncVersion: 6 } }
    })

    let outcomes: ConflictOutcome[] = []
    await act(async () => {
      outcomes = await result.current.resolveConflicts([
        { id: 'planner-0', choice: 'overwrite' },
        { id: 'planner-1', choice: 'overwrite' },
        { id: 'planner-2', choice: 'overwrite' },
      ])
    })

    expect(outcomes.map((outcome) => outcome.id)).toEqual(['planner-0', 'planner-1'])
    expect(outcomes[0]!.result.ok).toBe(true)
    expect(outcomes[1]!.result).toEqual({
      ok: false,
      error: { step: 'sync', error: { kind: 'unknown' } },
    })

    const attempted = syncMocks.syncToServer.mock.calls.map(
      (call) => (call[0] as SaveablePlanner).metadata.id,
    )
    expect(attempted).toEqual(['planner-0', 'planner-1'])

    // Only what resolved leaves the list; the rest stay for a second attempt.
    expect(result.current.pendingConflicts.map((conflict) => conflict.id)).toEqual([
      'planner-1',
      'planner-2',
    ])
  })

  it('clears the whole list when every resolution lands', async () => {
    const result = await pendingThreeConflicts()
    syncMocks.syncToServer.mockImplementation(async (planner: unknown) => ({
      planner,
      ack: { syncVersion: 6 },
    }))

    let outcomes: ConflictOutcome[] = []
    await act(async () => {
      outcomes = await result.current.resolveConflicts([
        { id: 'planner-0', choice: 'overwrite' },
        { id: 'planner-1', choice: 'discard' },
        { id: 'planner-2', choice: 'overwrite' },
      ])
    })

    expect(outcomes.every((outcome) => outcome.result.ok)).toBe(true)
    expect(result.current.pendingConflicts).toEqual([])
  })

  it('reads the server side again at resolution time, not the copy sync captured', async () => {
    const result = await pendingThreeConflicts()
    syncMocks.syncToServer.mockImplementation(async (planner: unknown) => ({
      planner,
      ack: { syncVersion: 6 },
    }))
    // The server moved on after the conflict was detected.
    syncMocks.fetchFromServer.mockResolvedValue({
      ok: true,
      value: {
        planner: {
          metadata: { id: 'planner-0', title: 'moved on', syncVersion: 11 },
          config: { type: 'MIRROR_DUNGEON', category: '5F' },
          content: {},
        },
        ack: { syncVersion: 11 },
      },
    })
    syncMocks.saveToLocal.mockClear()

    await act(async () => {
      await result.current.resolveConflicts([{ id: 'planner-0', choice: 'discard' }])
    })

    const stored = syncMocks.saveToLocal.mock.calls[0]![0] as SaveablePlanner
    expect(stored.metadata.syncVersion).toBe(11)
    expect(stored.metadata.title).toBe('moved on')
  })

  it('pushes the local row as it stands at resolution time, not as sync found it', async () => {
    const result = await pendingThreeConflicts()
    syncMocks.syncToServer.mockImplementation(async (planner: unknown) => ({
      planner,
      ack: { syncVersion: 6 },
    }))
    // The user edited the planner after the sync pass raised the conflict, and a
    // parked dialog makes that window unbounded.
    syncMocks.loadFromLocal.mockResolvedValue({
      ok: true,
      value: {
        metadata: { id: 'planner-0', title: 'edited after the sync pass', syncVersion: 1 },
        config: { type: 'MIRROR_DUNGEON', category: '5F' },
        content: {},
      },
    })
    syncMocks.saveToLocal.mockClear()

    await act(async () => {
      await result.current.resolveConflicts([{ id: 'planner-0', choice: 'overwrite' }])
    })

    const pushed = syncMocks.syncToServer.mock.calls[0]![0] as SaveablePlanner
    expect(pushed.metadata.title).toBe('edited after the sync pass')
    const stored = syncMocks.saveToLocal.mock.calls[0]![0] as SaveablePlanner
    expect(stored.metadata.title).toBe('edited after the sync pass')
  })

  it('reports a failure rather than pushing nothing when the local row is gone', async () => {
    const result = await pendingThreeConflicts()
    syncMocks.loadFromLocal.mockResolvedValue({ ok: true, value: null })

    let outcomes: ConflictOutcome[] = []
    await act(async () => {
      outcomes = await result.current.resolveConflicts([{ id: 'planner-0', choice: 'overwrite' }])
    })

    expect(outcomes[0]!.result.ok).toBe(false)
    expect(syncMocks.syncToServer).not.toHaveBeenCalled()
  })

  it('refuses to push a planner it cannot validate for want of the gift spec', async () => {
    // The spec load has not landed, so the affordability rules cannot run.
    syncMocks.egoGiftSpec = null
    const result = await pendingThreeConflicts()

    let outcomes: ConflictOutcome[] = []
    await act(async () => {
      outcomes = await result.current.resolveConflicts([{ id: 'planner-0', choice: 'overwrite' }])
    })

    expect(outcomes[0]!.result).toEqual({
      ok: false,
      error: { step: 'validate', error: { kind: 'retryable' } },
    })
    expect(syncMocks.syncToServer).not.toHaveBeenCalled()
  })

  it('re-interprets the plan an item already built, so a resubmission adds no copy', async () => {
    const result = await pendingThreeConflicts()
    // The first attempt fails at the copy's upload; the retry must reuse its id.
    syncMocks.syncToServer.mockImplementationOnce(async () => {
      throw new Error('upload rejected')
    })
    syncMocks.syncToServer.mockImplementation(async (planner: unknown) => ({
      planner,
      ack: { syncVersion: 6 },
    }))
    syncMocks.saveToLocal.mockClear()

    await act(async () => {
      await result.current.resolveConflicts([{ id: 'planner-0', choice: 'both' }])
    })
    await act(async () => {
      await result.current.resolveConflicts([{ id: 'planner-0', choice: 'both' }])
    })

    const copyIds = syncMocks.saveToLocal.mock.calls
      .map((call) => (call[0] as SaveablePlanner).metadata.id)
      .filter((id) => id !== 'planner-0')
    expect(copyIds).toHaveLength(2)
    expect(new Set(copyIds).size).toBe(1)
  })

  it('reports an unreadable device id against the submission, not its first row', async () => {
    const result = await pendingThreeConflicts()
    syncMocks.getOrCreateDeviceId.mockResolvedValueOnce({ ok: false, error: 'readFailed' })

    let outcomes: ConflictOutcome[] = []
    await act(async () => {
      outcomes = await result.current.resolveConflicts([{ id: 'planner-0', choice: 'overwrite' }])
    })

    expect(outcomes[0]!.result).toEqual({
      ok: false,
      error: { step: 'precondition', error: { kind: 'unknown' } },
    })
    expect(result.current.pendingConflicts).toHaveLength(3)
  })

  it('clears the resolving flag when a step throws, so the dialog is usable again', async () => {
    const result = await pendingThreeConflicts()
    syncMocks.syncToServer.mockImplementation(async (planner: unknown) => ({
      planner,
      ack: { syncVersion: 6 },
    }))
    // The post-resolution cache refresh is outside every per-item guard.
    syncMocks.listLocal.mockRejectedValue(new Error('local store went away'))

    await act(async () => {
      await expect(
        result.current.resolveConflicts([{ id: 'planner-0', choice: 'overwrite' }]),
      ).rejects.toThrow('local store went away')
    })

    expect(result.current.isResolvingConflicts).toBe(false)
  })
})
