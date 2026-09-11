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

import { renderHook, act, waitFor } from '@testing-library/react'
import { flushMicrotask } from '@/test-utils'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { queryClient } from '@/lib/queryClient'
import { createPlannerEditorStore } from '../../stores/usePlannerEditorStore'
import type { PlannerState, UsePlannerSaveOptions } from '../usePlannerSave'
import type { AppError } from '@/lib/apiErrorClassifier'
import type { Result } from '@/lib/result'
import type { SaveablePlanner } from '../../types/PlannerTypes'
import type { AcknowledgedPlanner } from '../usePlannerSyncAdapter'
import { BannedError, ConflictError, WriteTemporarilyUnavailableError } from '@/lib/apiErrors'
import { ok, err } from '@/lib/result'

// Shared call-order recorder: every adapter call pushes its label so order is assertable.
const callOrder: string[] = []

const mockSaveToLocal = vi.fn<(planner: SaveablePlanner) => Promise<Result<void, AppError>>>()
const mockSyncToServer =
  vi.fn<(planner: SaveablePlanner, force?: boolean) => Promise<AcknowledgedPlanner>>()
const mockFetchFromServer = vi.fn()
const mockDeleteFromLocal = vi.fn()

const mockOpenStorageDb = vi.fn(async () => ({}) as IDBDatabase)
vi.mock('@/lib/storage', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/storage')>()
  return { ...actual, openStorageDb: () => mockOpenStorageDb() }
})

vi.mock('../usePlannerStorage', () => ({
  usePlannerStorage: () => ({
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
  acknowledgedCopy: ({ planner, ack }: AcknowledgedPlanner) => ({
    ...planner,
    metadata: { ...planner.metadata, syncVersion: ack.syncVersion },
  }),
}))

/** Minted ids are counted: a retried resolution must not mint a second copy. */
const mockGenerateUUID = vi.fn(() => `uuid-${mockGenerateUUID.mock.calls.length}`)
vi.mock('@/lib/uuid', () => ({ generateUUID: () => mockGenerateUUID() }))

const mockUseAuthQuery = vi.fn()
vi.mock('@/shared/auth/hooks/useAuthQuery', () => ({
  useAuthQuery: () => mockUseAuthQuery(),
  authQueryKeys: { me: ['auth', 'me'] as const },
}))

vi.mock('@/pages/egoGift/hooks/useEGOGiftListData', () => ({
  useEGOGiftListSpec: () => ({ spec: {}, i18n: {} }).spec,
  useEGOGiftListI18n: () => ({ spec: {}, i18n: {} }).i18n,
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
    const { result, rerender } = renderHook(() => usePlannerSave(baseOptions()))

    await act(async () => {
      await result.current.save({ published: false })
    })

    rerender()
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
      key: 'planner:pages.plannerMD.publish.missingTitle',
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

    expect(result.current.error).toEqual({ kind: 'restricted', reason: 'banned' })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['auth', 'me'] })
    invalidateSpy.mockRestore()
  })

  it('maps WriteTemporarilyUnavailableError to an unavailable write (saved locally, sync paused)', async () => {
    authenticated()
    mockSyncToServer.mockRejectedValue(
      new WriteTemporarilyUnavailableError('Database temporarily unavailable, please retry'),
    )
    const { result } = renderHook(() => usePlannerSave(baseOptions()))

    await act(async () => {
      await result.current.save({ published: false })
    })

    expect(result.current.error).toEqual({ kind: 'unavailable', scope: 'write' })
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
    const onServerReload = vi.fn(() => true)
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
    const { result, rerender } = renderHook(() => usePlannerSave(options))
    await act(async () => {
      await result.current.save({ published: false })
    })
    rerender()
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
    rerender()
    expect(result.current.syncVersion).toBe(12)
  })

  it('leaves the version alone when the discard write fails', async () => {
    // Adopting before the write is confirmed would leave version=server with the
    // old local content still on disk.
    authenticated()
    const { result, rerender } = await driveIntoConflict({ initialSyncVersion: 2 })
    mockFetchFromServer.mockResolvedValue(ok(fetchedAt(9)))
    mockSaveToLocal.mockResolvedValue(err({ kind: 'quota' }))

    await act(async () => {
      await result.current.resolveConflict('discard')
    })

    rerender()
    expect(result.current.syncVersion).toBe(2)
  })

  it('discard reloads from server: fetch THEN local, fires onServerReload, clears conflict', async () => {
    authenticated()
    const onServerReload = vi.fn(() => true)
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
    const onServerReload = vi.fn(() => true)
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
    const onServerReload = vi.fn(() => true)
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
    const { result, rerender } = renderHook(() =>
      usePlannerSave(baseOptions({ initialPlannerId: plannerId, initialSyncVersion: 4 })),
    )

    await act(async () => {
      await result.current.save()
    })
    await act(async () => {
      await result.current.save()
    })

    expect(sentVersions).toEqual([4, 30])
    rerender()
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

/** A store stand-in whose change notifications the test drives by hand. */
function manualStore() {
  const listeners = new Set<() => void>()
  return {
    subscribe: (listener: () => void) => {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },
    notify: () => {
      for (const listener of listeners) listener()
    },
  }
}

/** Let the session open: the connection and the device id both resolve first. */
async function sessionReady() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0))
  })
}

const writtenTitles = () => mockSaveToLocal.mock.calls.map(([planner]) => planner.metadata.title)

/** A parked upload the test releases by hand. */
function parkNextSync() {
  let release: (() => void) | null = null
  mockSyncToServer.mockImplementation(
    (planner) =>
      new Promise((resolve) => {
        release = () => {
          resolve({ planner, ack: { syncVersion: 5 } })
        }
      }),
  )
  return () => release?.()
}

describe('usePlannerSave - write-through', () => {
  it('opens the storage connection at mount, before any edit can need it', async () => {
    authenticated()
    renderHook(() => usePlannerSave(baseOptions()))
    await sessionReady()

    expect(mockOpenStorageDb).toHaveBeenCalledTimes(1)
  })

  it('writes a store change once, inside the task that made it', async () => {
    authenticated()
    const store = manualStore()
    let currentState = validState({ title: 'before' })
    renderHook(() =>
      usePlannerSave(baseOptions({ getState: () => currentState, subscribe: store.subscribe })),
    )
    await sessionReady()
    mockSaveToLocal.mockClear()

    currentState = validState({ title: 'after' })
    store.notify()
    await flushMicrotask()

    expect(writtenTitles()).toEqual(['after'])
    const [firstCall] = mockSaveToLocal.mock.calls
    expect(firstCall?.[0].metadata.status).toBe('draft')
  })

  it('collapses every notification in one task into one write of the last state', async () => {
    authenticated()
    const store = manualStore()
    let currentState = validState({ title: 'before' })
    renderHook(() =>
      usePlannerSave(baseOptions({ getState: () => currentState, subscribe: store.subscribe })),
    )
    await sessionReady()
    mockSaveToLocal.mockClear()

    for (const title of ['one', 'two', 'three']) {
      currentState = validState({ title })
      store.notify()
    }
    await flushMicrotask()

    expect(writtenTitles()).toEqual(['three'])
  })

  it('writes nothing for a notification that changed nothing persistable', async () => {
    authenticated()
    const store = manualStore()
    const currentState = validState({ title: 'same' })
    renderHook(() =>
      usePlannerSave(baseOptions({ getState: () => currentState, subscribe: store.subscribe })),
    )
    await sessionReady()
    mockSaveToLocal.mockClear()

    store.notify()
    await flushMicrotask()

    expect(mockSaveToLocal).not.toHaveBeenCalled()
  })

  it('writes nothing when a planner is only mounted', async () => {
    authenticated()
    const store = manualStore()
    const currentState = validState({ title: 'opened' })
    renderHook(() =>
      usePlannerSave(baseOptions({ getState: () => currentState, subscribe: store.subscribe })),
    )
    await sessionReady()

    expect(mockSaveToLocal).not.toHaveBeenCalled()
  })

  it('surfaces a failed write as the save error', async () => {
    authenticated()
    mockSaveToLocal.mockResolvedValue(err({ kind: 'quota' }))
    const store = manualStore()
    let currentState = validState({ title: 'before' })
    const { result } = renderHook(() =>
      usePlannerSave(baseOptions({ getState: () => currentState, subscribe: store.subscribe })),
    )
    await sessionReady()

    currentState = validState({ title: 'after' })
    store.notify()
    await flushMicrotask()

    await waitFor(() => {
      expect(result.current.error?.kind).toBe('quota')
    })
  })
})

describe('usePlannerSave - edits during an in-flight manual save', () => {
  it('holds an edit made while a save is in flight, and writes it once the save lands', async () => {
    authenticated()
    const store = manualStore()
    let currentState = validState({ title: 'before' })
    const { result } = renderHook(() =>
      usePlannerSave(baseOptions({ getState: () => currentState, subscribe: store.subscribe })),
    )
    await sessionReady()

    const releaseSync = parkNextSync()
    let savePromise: Promise<boolean> | undefined
    await act(async () => {
      savePromise = result.current.save({ published: false })
      // Let performSave capture its snapshot and reach the parked upload.
      await new Promise((resolve) => setTimeout(resolve, 0))
    })
    mockSaveToLocal.mockClear()

    // The user keeps typing while the save is still in flight. The save's own
    // snapshot must land after nothing newer, so the edit waits.
    currentState = validState({ title: 'after' })
    store.notify()
    await flushMicrotask()
    expect(mockSaveToLocal).not.toHaveBeenCalled()

    await act(async () => {
      releaseSync()
      await savePromise
    })

    // The save's own snapshot lands first, the held edit right after it.
    await waitFor(() => {
      expect(writtenTitles()).toEqual(['before', 'after'])
    })
  })
})

describe('usePlannerSave - unmounting during a save', () => {
  it('writes an edit made while a save was in flight, after the editor is gone', async () => {
    authenticated()
    const store = manualStore()
    let currentState = validState({ title: 'before' })
    const { result, unmount } = renderHook(() =>
      usePlannerSave(baseOptions({ getState: () => currentState, subscribe: store.subscribe })),
    )
    await sessionReady()

    const releaseSync = parkNextSync()
    let savePromise: Promise<boolean> | undefined
    await act(async () => {
      savePromise = result.current.save({ published: false })
      await new Promise((resolve) => setTimeout(resolve, 0))
    })
    mockSaveToLocal.mockClear()

    currentState = validState({ title: 'after' })
    store.notify()

    // The editor closes while the save is still running. Nothing re-renders this
    // hook again, so anything reading save state off a render closure is frozen
    // from here on.
    await act(async () => {
      unmount()
    })

    await act(async () => {
      releaseSync()
      await savePromise
    })

    await waitFor(() => {
      expect(writtenTitles()).toEqual(['before', 'after'])
    })
  })
})

describe('usePlannerSave - discard adoption', () => {
  it('leaves the adopted server copy clean instead of written back as a draft', async () => {
    authenticated()
    const store = manualStore()
    let currentState = validState({ title: 'local' })
    // The reload rewrites the store with the server's content, as the shell does,
    // and reports that it adopted it.
    const onServerReload = vi.fn(() => {
      currentState = validState({ title: 'from server' })
      store.notify()
      return true
    })

    mockSyncToServer.mockRejectedValueOnce(new ConflictError('SYNC_CONFLICT', 'conflict', 7))
    const { result } = renderHook(() =>
      usePlannerSave(
        baseOptions({ getState: () => currentState, subscribe: store.subscribe, onServerReload }),
      ),
    )
    await sessionReady()

    await act(async () => {
      await result.current.save({ published: false })
    })
    expect(result.current.error?.kind).toBe('conflict')

    mockFetchFromServer.mockResolvedValue(ok(fetchedAt(9)))
    mockSaveToLocal.mockClear()

    await act(async () => {
      await result.current.resolveConflict('discard')
    })
    await flushMicrotask()

    // The reload's notification arrived while the resolution held the write, and
    // the release found the store equal to what the resolution wrote.
    expect(mockSaveToLocal).toHaveBeenCalledTimes(1)
  })

  it('keeps the planner unsynced when the reload refused the server copy', async () => {
    authenticated()
    const store = manualStore()
    let currentState = validState({ title: 'local' })
    // A consumer that rejects the copy leaves the old local content in the store.
    const onServerReload = vi.fn(() => false)

    mockSyncToServer.mockRejectedValueOnce(new ConflictError('SYNC_CONFLICT', 'conflict', 7))
    const { result } = renderHook(() =>
      usePlannerSave(
        baseOptions({ getState: () => currentState, subscribe: store.subscribe, onServerReload }),
      ),
    )
    await sessionReady()

    await act(async () => {
      await result.current.save({ published: false })
    })

    mockFetchFromServer.mockResolvedValue(ok(fetchedAt(9)))
    currentState = validState({ title: 'edited while conflicted' })
    act(() => {
      store.notify()
    })

    await act(async () => {
      await result.current.resolveConflict('discard')
    })

    expect(onServerReload).toHaveBeenCalledTimes(1)
    expect(result.current.error).not.toBeNull()
  })
})

describe('usePlannerSave - failed resolution surface', () => {
  /** Reach the conflict through the one public path that sets it: a rejected save. */
  async function driveIntoConflict(overrides: Partial<UsePlannerSaveOptions> = {}) {
    mockSyncToServer.mockRejectedValueOnce(new ConflictError('SYNC_CONFLICT', 'conflict', 7))
    const hook = renderHook(() => usePlannerSave(baseOptions(overrides)))

    await act(async () => {
      await hook.result.current.save({ published: false })
    })

    expect(hook.result.current.error?.kind).toBe('conflict')
    callOrder.length = 0
    return hook
  }

  it('reports a non-conflict resolution failure without displacing the conflict', async () => {
    authenticated()
    const { result } = await driveIntoConflict()
    mockFetchFromServer.mockResolvedValue(err({ kind: 'quota' }))

    await act(async () => {
      await result.current.resolveConflict('discard')
    })

    // The conflict is still unresolved, so its dialog stays; the failure that
    // stopped the resolution has nowhere else to be reported from.
    expect(result.current.error?.kind).toBe('conflict')
    expect(result.current.resolutionError).toEqual({ kind: 'quota' })
  })

  it('drops the previous resolution failure when a newer failure arrives', async () => {
    authenticated()
    const { result } = await driveIntoConflict()
    mockFetchFromServer.mockResolvedValue(err({ kind: 'quota' }))

    await act(async () => {
      await result.current.resolveConflict('discard')
    })
    expect(result.current.resolutionError).toEqual({ kind: 'quota' })

    // A fresh save conflicts again: the old reason describes a conflict that is
    // no longer the one on screen.
    mockSyncToServer.mockRejectedValueOnce(new ConflictError('SYNC_CONFLICT', 'conflict', 12))
    await act(async () => {
      await result.current.save({ published: false })
    })

    expect(result.current.error?.kind).toBe('conflict')
    expect(result.current.resolutionError).toBeNull()
  })

  it('clears the previous resolution failure when a later attempt succeeds', async () => {
    authenticated()
    const onServerReload = vi.fn(() => true)
    const { result } = await driveIntoConflict({ onServerReload })
    mockFetchFromServer.mockResolvedValue(err({ kind: 'quota' }))

    await act(async () => {
      await result.current.resolveConflict('discard')
    })
    expect(result.current.resolutionError).not.toBeNull()

    mockFetchFromServer.mockResolvedValue(ok(fetchedAt(9)))
    await act(async () => {
      await result.current.resolveConflict('discard')
    })

    expect(result.current.error).toBeNull()
    expect(result.current.resolutionError).toBeNull()
  })
})

describe('usePlannerSave - retried resolution', () => {
  const PLANNER_ID = '11111111-1111-4111-8111-111111111111'

  /** Reach the conflict through the one public path that sets it: a rejected save. */
  async function driveIntoConflict() {
    mockSyncToServer.mockRejectedValueOnce(new ConflictError('SYNC_CONFLICT', 'conflict', 7))
    const hook = renderHook(() =>
      usePlannerSave(baseOptions({ initialPlannerId: PLANNER_ID, onKeepBothCreated: vi.fn() })),
    )

    await act(async () => {
      await hook.result.current.save({ published: false })
    })

    expect(hook.result.current.error?.kind).toBe('conflict')
    callOrder.length = 0
    return hook
  }

  it('re-interprets the plan it already built, so a retried keep-both mints one copy', async () => {
    authenticated()
    mockFetchFromServer.mockResolvedValue(ok(fetchedAt(9)))
    const { result } = await driveIntoConflict()

    // The copy is written locally, then its upload is rejected: the resolution
    // fails and the copy is rolled back.
    mockSyncToServer.mockRejectedValueOnce(new Error('upload rejected'))
    mockGenerateUUID.mockClear()

    await act(async () => {
      await result.current.resolveConflict('both')
    })
    expect(result.current.error?.kind).toBe('conflict')

    await act(async () => {
      await result.current.resolveConflict('both')
    })

    // Planning again would mint a second identity, and the retry would leave the
    // rolled-back copy's twin behind.
    expect(mockGenerateUUID).toHaveBeenCalledTimes(1)
    // Every write the minter named: one copy id, written twice under the same identity.
    const copyIds = mockSaveToLocal.mock.calls
      .map((call) => call[0].metadata.id)
      .filter((id) => typeof id === 'string' && id.startsWith('uuid-'))
    expect(copyIds).toHaveLength(2)
    expect(new Set(copyIds).size).toBe(1)
  })
})
