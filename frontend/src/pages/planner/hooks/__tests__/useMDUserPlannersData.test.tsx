/**
 * useMDUserPlannersData.test.tsx
 *
 * Tests for user (personal) planners data hook.
 * Uses Vitest for testing query key structure.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import type { SaveablePlanner } from '../../types/PlannerTypes'

const syncMocks = vi.hoisted(() => ({
  isAuthenticated: true,
  syncEnabled: true,
  listFromServer: vi.fn(async (): Promise<unknown[]> => []),
  listLocal: vi.fn(async (): Promise<unknown[]> => []),
}))

// Both real hooks return a fresh object literal per render, so the mocks do too:
// dependency-array stability must not rest on their identity.
vi.mock('../usePlannerStorage', () => ({
  usePlannerStorage: () => ({
    getOrCreateDeviceId: vi.fn(async () => 'test-device'),
    saveToLocal: vi.fn(async () => ({ success: true })),
    loadFromLocal: vi.fn(async () => null),
    listLocal: syncMocks.listLocal,
    listLocalFull: vi.fn(async () => []),
    deleteFromLocal: vi.fn(async () => undefined),
    clearCorruptedLocal: vi.fn(async () => undefined),
  }),
}))

vi.mock('../usePlannerSyncAdapter', () => ({
  usePlannerSyncAdapter: () => ({
    syncToServer: vi.fn(),
    fetchFromServer: vi.fn(async () => null),
    deleteFromServer: vi.fn(),
    listFromServer: syncMocks.listFromServer,
  }),
}))

vi.mock('@/shared/auth', () => ({
  useAuthQuery: () => ({ data: syncMocks.isAuthenticated ? { id: 'user-1' } : null }),
}))

vi.mock('@/pages/settings', () => ({
  useUserSettingsQuery: () => ({ data: { syncEnabled: syncMocks.syncEnabled } }),
}))

vi.mock('@/pages/egoGift', () => ({
  useEGOGiftListData: () => ({ spec: null, i18n: null }),
}))

import {
  userPlannersQueryKeys,
  adoptSyncedVersion,
  useMDUserPlannersData,
} from '../useMDUserPlannersData'

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

describe('adoptSyncedVersion', () => {
  function makePlanner(syncVersion: number, title: string): SaveablePlanner {
    return {
      metadata: {
        id: '11111111-2222-3333-4444-555555555555',
        title,
        status: 'draft',
        syncVersion,
        savedAt: null,
      },
      config: { type: 'MIRROR_DUNGEON', category: '5F' },
      content: { title },
    } as unknown as SaveablePlanner
  }

  it('keeps the local content but adopts the server-assigned syncVersion', () => {
    const local = makePlanner(1, 'Local draft')
    const synced = makePlanner(7, 'Server echo')

    const saved = adoptSyncedVersion(local, synced)

    expect(saved.metadata.syncVersion).toBe(7)
    expect(saved.content).toEqual(local.content)
    expect(saved.metadata.title).toBe('Local draft')
  })

  it('marks the planner saved with a savedAt timestamp', () => {
    const saved = adoptSyncedVersion(makePlanner(1, 'A'), makePlanner(2, 'A'))

    expect(saved.metadata.status).toBe('saved')
    expect(saved.metadata.savedAt).not.toBeNull()
  })
})
