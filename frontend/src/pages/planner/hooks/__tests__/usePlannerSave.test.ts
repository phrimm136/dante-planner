/**
 * usePlannerSave.test.ts
 *
 * Characterization (golden-master) tests for the unified planner save hook.
 * Pins CURRENT observable behavior (Inv 5), including the validate -> sync -> local
 * ordering inside performSave. These tests assert what the code DOES today;
 * they do NOT encode the deferred reordering track.
 *
 * Mocking policy: ONLY the two split adapters (usePlannerStorage /
 * usePlannerSyncAdapter), useAuthQuery, useEGOGiftListData, and react-i18next are faked.
 * The real validators in plannerValidation run through the hook against valid/invalid
 * content fixtures — plannerValidation is deliberately NOT mocked.
 */

import { renderHook, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { queryClient } from '@/lib/queryClient'
import { createPlannerEditorStore } from '../../stores/usePlannerEditorStore'
import type { PlannerState, UsePlannerSaveOptions } from '../usePlannerSave'
import type { SaveError } from '../../lib/plannerSaveErrors'
import type { Result } from '@/lib/result'
import type { SaveablePlanner } from '../../types/PlannerTypes'
import type { AcknowledgedPlanner } from '../usePlannerSyncAdapter'
import { BannedError, ConflictError, WriteTemporarilyUnavailableError } from '@/lib/api'
import { ok, err } from '@/lib/result'

// Shared call-order recorder: every adapter call pushes its label so order is assertable.
const callOrder: string[] = []

const mockGetOrCreateDeviceId = vi.fn<() => Promise<string>>()
const mockSaveToLocal = vi.fn<(planner: SaveablePlanner) => Promise<Result<void, SaveError>>>()
const mockSyncToServer =
  vi.fn<(planner: SaveablePlanner, force?: boolean) => Promise<AcknowledgedPlanner>>()
const mockFetchFromServer = vi.fn()
const mockDeleteFromLocal = vi.fn()

vi.mock('../usePlannerStorage', () => ({
  usePlannerStorage: () => ({
    getOrCreateDeviceId: mockGetOrCreateDeviceId,
    saveToLocal: (planner: SaveablePlanner) => {
      callOrder.push('local')
      return mockSaveToLocal(planner)
    },
    deleteFromLocal: mockDeleteFromLocal,
    loadFromLocal: vi.fn(),
    listLocal: vi.fn(),
    listLocalFull: vi.fn(),
  }),
}))

vi.mock('../usePlannerSyncAdapter', () => ({
  usePlannerSyncAdapter: () => ({
    syncToServer: (planner: SaveablePlanner, force?: boolean) => {
      callOrder.push('sync')
      return mockSyncToServer(planner, force)
    },
    fetchFromServer: (id: string) => {
      callOrder.push('fetch')
      return mockFetchFromServer(id)
    },
    deleteFromServer: vi.fn(),
    listFromServer: vi.fn(),
  }),
}))

const mockUseAuthQuery = vi.fn()
vi.mock('@/shared/auth/hooks/useAuthQuery', () => ({
  useAuthQuery: () => mockUseAuthQuery(),
  authQueryKeys: { me: ['auth', 'me'] as const },
}))

vi.mock('@/pages/egoGift/hooks/useEGOGiftListData', () => ({
  useEGOGiftListData: () => ({ spec: {}, i18n: {} }),
}))

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (_key: string, fallback?: string) => fallback ?? _key,
    }),
  }
})

import { usePlannerSave } from '../usePlannerSave'

/**
 * Build a genuinely-valid PlannerState from the store factory: all 12 sinners,
 * skill EA summing to 6, real Set instances (required by serializeSets).
 */
function validState(overrides: Partial<PlannerState> = {}): PlannerState {
  const base = createPlannerEditorStore().getState().getPlannerState()
  return { ...base, ...overrides }
}

function baseOptions(overrides: Partial<UsePlannerSaveOptions> = {}): UsePlannerSaveOptions {
  const state = overrides.getState?.() ?? validState()
  return {
    getState: () => state,
    subscribe: () => () => {},
    schemaVersion: 2,
    contentVersion: 6,
    plannerType: 'MIRROR_DUNGEON',
    syncEnabled: true,
    ...overrides,
  }
}

/** A write outcome standing in for the adapter: the server assigned `syncVersion`. */
function syncedFrom(planner: SaveablePlanner, syncVersion: number): Promise<AcknowledgedPlanner> {
  return Promise.resolve({
    planner: { ...planner, metadata: { ...planner.metadata, syncVersion } },
    ack: { syncVersion },
  })
}

/** A fetch outcome carrying the version the server holds. */
function fetchedAt(syncVersion: number): AcknowledgedPlanner {
  return {
    planner: { metadata: { syncVersion } } as SaveablePlanner,
    ack: { syncVersion },
  }
}

function authenticated() {
  mockUseAuthQuery.mockReturnValue({
    data: { id: 'u1', isBanned: false, isTimedOut: false },
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  callOrder.length = 0
  mockGetOrCreateDeviceId.mockResolvedValue('device-123')
  mockSaveToLocal.mockResolvedValue(ok(undefined))
  mockDeleteFromLocal.mockResolvedValue(ok(undefined))
  mockSyncToServer.mockImplementation((planner) => syncedFrom(planner, 5))
})

describe('usePlannerSave - save() golden master', () => {
  it('returns true and runs sync BEFORE local for an authenticated, sync-enabled valid MD draft', async () => {
    authenticated()
    const { result } = renderHook(() => usePlannerSave(baseOptions()))

    let outcome: boolean | undefined
    await act(async () => {
      outcome = await result.current.save({ published: false })
    })

    expect(outcome).toBe(true)
    expect(callOrder).toEqual(['sync', 'local'])
    expect(mockSyncToServer).toHaveBeenCalledTimes(1)
    expect(mockSaveToLocal).toHaveBeenCalledTimes(1)
    expect(result.current.error).toBeNull()
  })

  it('skips sync when not authenticated but still saves locally', async () => {
    mockUseAuthQuery.mockReturnValue({ data: null })
    const { result } = renderHook(() => usePlannerSave(baseOptions()))

    let outcome: boolean | undefined
    await act(async () => {
      outcome = await result.current.save({ published: false })
    })

    expect(outcome).toBe(true)
    expect(callOrder).toEqual(['local'])
    expect(mockSyncToServer).not.toHaveBeenCalled()
  })

  it('skips sync when authenticated but sync disabled and not forced', async () => {
    authenticated()
    const { result } = renderHook(() => usePlannerSave(baseOptions({ syncEnabled: false })))

    await act(async () => {
      await result.current.save({ published: false })
    })

    expect(callOrder).toEqual(['local'])
    expect(mockSyncToServer).not.toHaveBeenCalled()
  })

  it('forceSync syncs even when auto-sync is disabled', async () => {
    authenticated()
    const { result } = renderHook(() => usePlannerSave(baseOptions({ syncEnabled: false })))

    await act(async () => {
      await result.current.save({ published: false, forceSync: true })
    })

    expect(callOrder).toEqual(['sync', 'local'])
  })

  it('adopts the syncVersion returned by the server', async () => {
    authenticated()
    mockSyncToServer.mockImplementation((planner) => syncedFrom(planner, 42))
    const { result } = renderHook(() => usePlannerSave(baseOptions()))

    await act(async () => {
      await result.current.save({ published: false })
    })

    expect(result.current.syncVersion).toBe(42)
  })
})

describe('usePlannerSave - validation-first (Inv 5)', () => {
  it('returns false and calls NEITHER sync nor local when validation fails', async () => {
    authenticated()
    const invalid = validState({ deploymentOrder: [99] })
    const { result } = renderHook(() => usePlannerSave(baseOptions({ getState: () => invalid })))

    let outcome: boolean | undefined
    await act(async () => {
      outcome = await result.current.save({ published: false })
    })

    expect(outcome).toBe(false)
    expect(callOrder).toEqual([])
    expect(mockSyncToServer).not.toHaveBeenCalled()
    expect(mockSaveToLocal).not.toHaveBeenCalled()
  })

  it('surfaces a user-friendly validation error code on failure', async () => {
    authenticated()
    const invalid = validState({ deploymentOrder: [99] })
    const { result } = renderHook(() => usePlannerSave(baseOptions({ getState: () => invalid })))

    await act(async () => {
      await result.current.save({ published: false })
    })

    expect(result.current.error?.kind).toBe('validation')
    expect(result.current.error).toMatchObject({ kind: 'validation', key: expect.any(String) })
  })
})

describe('usePlannerSave - draft vs published path selection', () => {
  it('published=true exercises the strict path and fails a titleless planner', async () => {
    authenticated()
    const titleless = validState({ title: '' })
    const { result } = renderHook(() => usePlannerSave(baseOptions({ getState: () => titleless })))

    let outcome: boolean | undefined
    await act(async () => {
      outcome = await result.current.save({ published: true })
    })

    expect(outcome).toBe(false)
    expect(callOrder).toEqual([])
    expect(result.current.error).toMatchObject({
      kind: 'validation',
      key: 'pages.plannerMD.publish.missingTitle',
    })
  })

  it('published=false exercises the non-strict path and accepts a titleless planner', async () => {
    authenticated()
    const titleless = validState({ title: '' })
    const { result } = renderHook(() => usePlannerSave(baseOptions({ getState: () => titleless })))

    let outcome: boolean | undefined
    await act(async () => {
      outcome = await result.current.save({ published: false })
    })

    expect(outcome).toBe(true)
    expect(callOrder).toEqual(['sync', 'local'])
  })
})

describe('usePlannerSave - error surface', () => {
  it('surfaces the quota failure the storage layer reported', async () => {
    authenticated()
    mockSaveToLocal.mockResolvedValue(err({ kind: 'quota' }))
    const { result } = renderHook(() => usePlannerSave(baseOptions()))

    let outcome: boolean | undefined
    await act(async () => {
      outcome = await result.current.save({ published: false })
    })

    expect(outcome).toBe(false)
    expect(result.current.error).toEqual({ kind: 'quota' })
  })

  it('invalidates the cached account when a restriction blocks the save', async () => {
    // The app-wide restriction banner reads that cache entry, so a save the server
    // refused on moderation grounds has to refresh it.
    authenticated()
    mockSyncToServer.mockRejectedValue(new BannedError('banned'))
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    const { result } = renderHook(() => usePlannerSave(baseOptions()))

    await act(async () => {
      await result.current.save({ published: false })
    })

    expect(result.current.error).toEqual({ kind: 'moderation', reason: 'banned' })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['auth', 'me'] })
    invalidateSpy.mockRestore()
  })

  it('maps WriteTemporarilyUnavailableError to a syncPaused degradation (saved locally, sync paused)', async () => {
    authenticated()
    mockSyncToServer.mockRejectedValue(
      new WriteTemporarilyUnavailableError('Database temporarily unavailable, please retry'),
    )
    const { result } = renderHook(() => usePlannerSave(baseOptions()))

    await act(async () => {
      await result.current.save({ published: false })
    })

    expect(result.current.error).toEqual({ kind: 'syncPaused' })
  })

  it('clearError resets error state', async () => {
    authenticated()
    const invalid = validState({ deploymentOrder: [99] })
    const { result } = renderHook(() => usePlannerSave(baseOptions({ getState: () => invalid })))

    await act(async () => {
      await result.current.save({ published: false })
    })
    expect(result.current.error?.kind).toBe('validation')

    act(() => {
      result.current.clearError()
    })
    expect(result.current.error).toBeNull()
  })

  it('reports restriction state for a banned user', () => {
    mockUseAuthQuery.mockReturnValue({
      data: { id: 'u1', isBanned: true, banReason: 'spam' },
    })
    const { result } = renderHook(() => usePlannerSave(baseOptions()))

    expect(result.current.isRestricted).toBe(true)
    expect(result.current.restrictionReason).toBe('spam')
  })

  it('reports no restriction reason for an unrestricted user', () => {
    authenticated()
    const { result } = renderHook(() => usePlannerSave(baseOptions()))

    expect(result.current.isRestricted).toBe(false)
    expect(result.current.restrictionReason).toBeUndefined()
  })
})

describe('usePlannerSave - dirty tracking surface', () => {
  // Dirty tracking is NOT store state; it lives here as ref-baseline comparisons.
  // Baselines start uninitialized ('') so both flags read false until a save sets them.
  it('reports no unsynced / no local-unsaved changes against an uninitialized baseline', () => {
    authenticated()
    const { result } = renderHook(() => usePlannerSave(baseOptions()))

    expect(result.current.hasUnsyncedChanges).toBe(false)
    expect(result.current.hasLocalUnsavedChanges).toBe(false)
  })

  it('clears unsynced state after a successful manual save sets the synced baseline', async () => {
    authenticated()
    const { result } = renderHook(() => usePlannerSave(baseOptions()))

    await act(async () => {
      await result.current.save({ published: false })
    })

    expect(result.current.hasUnsyncedChanges).toBe(false)
    expect(result.current.hasLocalUnsavedChanges).toBe(false)
  })
})

describe('usePlannerSave - resolveConflict adapter ordering (golden master)', () => {
  /**
   * The conflict error is set only when a save throws a ConflictError. The single
   * supported way to reach it from the public surface is a save() whose syncToServer
   * rejects with ConflictError. That triggering save pushes 'sync' onto callOrder, so
   * each test clears callOrder before invoking resolveConflict to isolate the branch.
   */
  async function driveIntoConflict(
    overrides: Partial<UsePlannerSaveOptions> = {},
    serverVersion: number | null = 7,
  ) {
    mockSyncToServer.mockRejectedValueOnce(
      new ConflictError('SYNC_CONFLICT', 'conflict', serverVersion),
    )
    const hook = renderHook(() => usePlannerSave(baseOptions(overrides)))

    await act(async () => {
      await hook.result.current.save({ published: false })
    })

    expect(hook.result.current.error?.kind).toBe('conflict')
    callOrder.length = 0
    return hook
  }

  beforeEach(() => {
    // Resolution branches read a server planner; default it to a truthy value with a
    // syncVersion so the if (serverPlanner) blocks execute (fetch -> local -> callbacks).
    mockFetchFromServer.mockResolvedValue(ok(fetchedAt(9)))
  })

  /** Record the version each force push presents, once the conflict is already set. */
  function captureSentVersionsOnce(): number[] {
    const sentVersions: number[] = []
    mockSyncToServer.mockImplementation((planner: SaveablePlanner) => {
      sentVersions.push(planner.metadata.syncVersion)
      return syncedFrom(planner, planner.metadata.syncVersion)
    })
    return sentVersions
  }

  it('overwrite force-saves via performSave: sync THEN local, clears conflict', async () => {
    authenticated()
    const onServerReload = vi.fn()
    const { result } = await driveIntoConflict({ onServerReload })

    let outcome: boolean | undefined
    await act(async () => {
      outcome = await result.current.resolveConflict('overwrite')
    })

    expect(outcome).toBe(true)
    expect(callOrder).toEqual(['sync', 'local'])
    expect(onServerReload).not.toHaveBeenCalled()
    expect(result.current.error).toBeNull()
  })

  it('never lowers the presented version on the pre-force-push read', async () => {
    // The read adopts no content, so taking a lower server version would present a
    // version the local copy has already passed and overwrite the server from behind.
    // It runs only when the conflict reported no server version, and initialSyncVersion
    // must stay undefined or presentedVersion's max would mask a lowered ref.
    authenticated()
    const plannerId = '11111111-1111-4111-8111-111111111111'
    const options = baseOptions({ initialPlannerId: plannerId })

    // Advance the ref to 12 through a confirmed ack, not through a prop.
    mockSyncToServer.mockImplementation((planner) => syncedFrom(planner, 12))
    const { result } = renderHook(() => usePlannerSave(options))
    await act(async () => {
      await result.current.save({ published: false })
    })
    expect(result.current.syncVersion).toBe(12)

    // A conflict carrying no server version is what routes through the read.
    mockSyncToServer.mockRejectedValueOnce(new ConflictError('SYNC_CONFLICT', 'conflict', null))
    await act(async () => {
      await result.current.save({ published: false })
    })
    expect(result.current.error?.kind).toBe('conflict')

    mockFetchFromServer.mockResolvedValue(ok(fetchedAt(3)))
    const sentVersions = captureSentVersionsOnce()

    await act(async () => {
      await result.current.resolveConflict('overwrite')
    })

    expect(sentVersions).toEqual([12])
    expect(result.current.syncVersion).toBe(12)
  })

  it('leaves the version alone when the discard write fails', async () => {
    // Adopting before the write is confirmed would leave version=server with the
    // old local content still on disk.
    authenticated()
    const { result } = await driveIntoConflict({ initialSyncVersion: 2 })
    mockFetchFromServer.mockResolvedValue(ok(fetchedAt(9)))
    mockSaveToLocal.mockResolvedValue(err({ kind: 'quota' }))

    await act(async () => {
      await result.current.resolveConflict('discard')
    })

    expect(result.current.syncVersion).toBe(2)
  })

  it('discard reloads from server: fetch THEN local, fires onServerReload, clears conflict', async () => {
    authenticated()
    const onServerReload = vi.fn()
    const { result } = await driveIntoConflict({ onServerReload })

    let outcome: boolean | undefined
    await act(async () => {
      outcome = await result.current.resolveConflict('discard')
    })

    expect(outcome).toBe(true)
    expect(callOrder).toEqual(['fetch', 'local'])
    expect(onServerReload).toHaveBeenCalledTimes(1)
    expect(result.current.error).toBeNull()
  })

  it('discard reports failure and keeps the conflict when the server read fails', async () => {
    // Silent data loss: a discard whose GET fails must never report success, or the
    // editor keeps holding content the user believes it threw away.
    authenticated()
    const onServerReload = vi.fn()
    const { result } = await driveIntoConflict({ onServerReload })

    mockFetchFromServer.mockResolvedValue(err({ kind: 'unknown' }))

    let outcome: boolean | undefined
    await act(async () => {
      outcome = await result.current.resolveConflict('discard')
    })

    expect(outcome).toBe(false)
    expect(result.current.error?.kind).toBe('conflict')
    expect(onServerReload).not.toHaveBeenCalled()
    expect(callOrder).toEqual(['fetch'])
  })

  it('overwrite reports failure and never force-saves when the server version is unreadable', async () => {
    // The conflict reported no server version, so performSave would push over the
    // server from an unanchored local syncVersion if the fetch failure were ignored.
    authenticated()
    const { result } = await driveIntoConflict({}, null)

    mockFetchFromServer.mockResolvedValue(err({ kind: 'unknown' }))

    let outcome: boolean | undefined
    await act(async () => {
      outcome = await result.current.resolveConflict('overwrite')
    })

    expect(outcome).toBe(false)
    expect(callOrder).toEqual(['fetch'])
    expect(result.current.error?.kind).toBe('conflict')
  })

  it('both forks a copy then reverts original: local, sync, fetch, local (non-ideal but current)', async () => {
    // Warning: keep-both saves the copy locally, syncs the copy, fetches the original,
    // then saves the original locally — TWO saveToLocal calls in this exact order.
    authenticated()
    const onServerReload = vi.fn()
    const onKeepBothCreated = vi.fn()
    const { result } = await driveIntoConflict({
      onServerReload,
      onKeepBothCreated,
    })

    let outcome: boolean | undefined
    await act(async () => {
      outcome = await result.current.resolveConflict('both')
    })

    expect(outcome).toBe(true)
    expect(callOrder).toEqual(['local', 'sync', 'fetch', 'local'])
    expect(onKeepBothCreated).toHaveBeenCalledTimes(1)
    expect(onKeepBothCreated).toHaveBeenCalledWith(expect.any(String))
    expect(onServerReload).toHaveBeenCalledTimes(1)
    expect(result.current.error).toBeNull()
  })

  it('both skips the copy sync when not authenticated: local, fetch, local', async () => {
    // Warning: keep-both only syncs the copy when authenticated; the original revert
    // still runs (fetch + local) regardless of auth. The conflict can only be reached
    // while authenticated (sync throws ConflictError), so flip auth off and rerender
    // before resolving to isolate the unauthenticated keep-both path.
    authenticated()
    const onKeepBothCreated = vi.fn()
    const { result, rerender } = await driveIntoConflict({ onKeepBothCreated })

    mockUseAuthQuery.mockReturnValue({ data: null })
    rerender()

    let outcome: boolean | undefined
    await act(async () => {
      outcome = await result.current.resolveConflict('both')
    })

    expect(outcome).toBe(true)
    expect(callOrder).toEqual(['local', 'fetch', 'local'])
    expect(onKeepBothCreated).toHaveBeenCalledTimes(1)
  })
})

describe('usePlannerSave - cross-surface version convergence', () => {
  // performSave mutates the payload object after the server responds (it writes
  // the returned syncVersion back into `saveable`), so mock.calls[i][0] reads the
  // post-mutation value — the presented version must be captured at call time.
  function captureSentVersions(): number[] {
    const sentVersions: number[] = []
    mockSyncToServer.mockImplementation((planner: SaveablePlanner) => {
      sentVersions.push(planner.metadata.syncVersion)
      return syncedFrom(planner, planner.metadata.syncVersion + 1)
    })
    return sentVersions
  }

  it('presents the advanced initialSyncVersion on the next save after a rerender', async () => {
    authenticated()
    const plannerId = '11111111-1111-4111-8111-111111111111'
    const sentVersions = captureSentVersions()
    const { result, rerender } = renderHook(
      (props: UsePlannerSaveOptions) => usePlannerSave(props),
      {
        initialProps: baseOptions({
          initialPlannerId: plannerId,
          initialSyncVersion: 1,
        }),
      },
    )

    // Another surface (publish header, conflict force) advanced the server
    // version; its write-through re-renders this hook with the new version.
    rerender(baseOptions({ initialPlannerId: plannerId, initialSyncVersion: 5 }))

    await act(async () => {
      await result.current.save()
    })

    expect(sentVersions).toEqual([5])
  })

  it('never rolls the version back when a lagging initialSyncVersion arrives', async () => {
    authenticated()
    const plannerId = '11111111-1111-4111-8111-111111111111'
    const sentVersions = captureSentVersions()
    const { result, rerender } = renderHook(
      (props: UsePlannerSaveOptions) => usePlannerSave(props),
      {
        initialProps: baseOptions({
          initialPlannerId: plannerId,
          initialSyncVersion: 6,
        }),
      },
    )

    await act(async () => {
      await result.current.save()
    })

    rerender(baseOptions({ initialPlannerId: plannerId, initialSyncVersion: 3 }))

    await act(async () => {
      await result.current.save()
    })

    expect(sentVersions).toEqual([6, 7])
  })

  it("adopts the ack's version, so the next save presents what the server assigned", async () => {
    authenticated()
    const plannerId = '11111111-1111-4111-8111-111111111111'
    const sentVersions: number[] = []
    mockSyncToServer.mockImplementation((planner: SaveablePlanner) => {
      sentVersions.push(planner.metadata.syncVersion)
      // The server jumps the version rather than incrementing it.
      return syncedFrom(planner, 30)
    })
    const { result } = renderHook(() =>
      usePlannerSave(baseOptions({ initialPlannerId: plannerId, initialSyncVersion: 4 })),
    )

    await act(async () => {
      await result.current.save()
    })
    await act(async () => {
      await result.current.save()
    })

    expect(sentVersions).toEqual([4, 30])
    expect(result.current.syncVersion).toBe(30)
  })

  it('refreshes the planner and userPlanners caches after a synced save', async () => {
    authenticated()
    const plannerId = '11111111-1111-4111-8111-111111111111'
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    const { result } = renderHook(() =>
      usePlannerSave(baseOptions({ initialPlannerId: plannerId, initialSyncVersion: 1 })),
    )

    await act(async () => {
      await result.current.save()
    })

    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ['planners', 'detail', plannerId],
    })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['userPlanners'] })
    invalidateSpy.mockRestore()
  })
})
