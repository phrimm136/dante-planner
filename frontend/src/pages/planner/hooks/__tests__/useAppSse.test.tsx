import { renderHook, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

import { SSE_CONNECTION, SSE_EVENTS } from '@/lib/constants'
import { useSseStore } from '@/shared/sse'

/**
 * Characterization tests for useAppSse — the SSE orchestration hook, exercising
 * the REAL generic engine (@/shared/sse) end-to-end.
 *
 * Scope (deliberately narrow): the three seams the shared→pages split endangers —
 * (a) the connection gating truth-table (auth × settings), (b) the event→effect
 * map (which SSE event drives which cache invalidation / side-effect), and
 * (c) unmount cleanup. Reconnect/backoff timing lives in the engine's own DI
 * tests, not here.
 *
 * Only leaf deps are mocked. Planner leaf modules are mocked by their relative
 * paths (the hook imports them intra-slice); auth/settings/notifications by
 * their public specifiers. The engine + store are real.
 */

// ── Controllable EventSource double + mutable auth/settings refs ──
const h = vi.hoisted(() => {
  class MockEventSource {
    static instances: MockEventSource[] = []
    url: string
    onopen: (() => void) | null = null
    onerror: (() => void) | null = null
    listeners: Record<string, ((e: { data: string }) => void)[]> = {}
    closed = false
    constructor(url: string) {
      this.url = url
      MockEventSource.instances.push(this)
    }
    addEventListener(type: string, cb: (e: { data: string }) => void) {
      ;(this.listeners[type] ??= []).push(cb)
    }
    close() {
      this.closed = true
    }
    // test drivers
    fireOpen() {
      this.onopen?.()
    }
    fireError() {
      this.onerror?.()
    }
    emit(type: string, data: unknown) {
      ;(this.listeners[type] ?? []).forEach((cb) => cb({ data: JSON.stringify(data) }))
    }
  }
  return {
    MockEventSource,
    userRef: { current: null as unknown },
    settingsRef: { current: null as unknown },
    createEventsConnection: vi.fn(() => new MockEventSource('/api/planner/events')),
    deleteFromLocal: vi.fn(() => Promise.resolve()),
    showBrowserNotification: vi.fn(),
    showNotificationToast: vi.fn(),
    isTabHidden: vi.fn(() => false),
    PLANNER_KEYS: {
      all: ['planners'] as const,
      list: () => ['planners', 'list'] as const,
      detail: (id: string) => ['planners', 'detail', id] as const,
    },
    USER_PLANNER_KEYS: { all: ['userPlanners'] as const },
    NOTIFICATION_KEYS: { all: ['notifications'] as const },
  }
})

// Planner leaf modules — mocked by the same relative specifiers the hook imports.
vi.mock('../../lib/plannerApi', () => ({
  plannerApi: { createEventsConnection: h.createEventsConnection },
}))
vi.mock('../usePlannerSaveAdapter', () => ({
  usePlannerSaveAdapter: () => ({ deleteFromLocal: h.deleteFromLocal }),
}))
vi.mock('../usePlannerSync', () => ({ plannerQueryKeys: h.PLANNER_KEYS }))
vi.mock('../useMDUserPlannersData', () => ({ userPlannersQueryKeys: h.USER_PLANNER_KEYS }))
vi.mock('../../schemas/PlannerSchemas', () => ({
  PlannerSseEventSchema: { parse: (x: unknown) => x },
}))

vi.mock('@/pages/settings', () => ({
  useUserSettingsQuery: () => ({ data: h.settingsRef.current, isLoading: false }),
}))

vi.mock('@/shared/auth', () => ({
  useAuthQueryNonBlocking: () => ({ data: h.userRef.current }),
}))

// Real engine + store (the 10 engine-driven tests rely on them); only the
// envelope schema is a pass-through so handlers see the raw payload verbatim.
vi.mock('@/shared/sse', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/sse')>()),
  SseEnvelopeSchema: {
    parse: (x: unknown) => x,
    safeParse: (x: unknown) => ({ success: true, data: x }),
  },
}))

vi.mock('@/shared/notifications', () => ({
  showBrowserNotification: h.showBrowserNotification,
  showNotificationToast: h.showNotificationToast,
  isTabHidden: h.isTabHidden,
  SseNotificationEventSchema: { safeParse: (x: unknown) => ({ success: true, data: x }) },
  SsePublishedEventSchema: { safeParse: (x: unknown) => ({ success: true, data: x }) },
  notificationQueryKeys: h.NOTIFICATION_KEYS,
}))

vi.mock('@/lib/i18n', () => ({ default: { t: (k: string) => k } }))
vi.mock('@/lib/formatUsername', () => ({ formatUsername: (a: string, b: string) => `${a}-${b}` }))

import { useAppSse } from '../useAppSse'

const SYNC_ON = {
  syncEnabled: true,
  notifyComments: false,
  notifyRecommendations: false,
  notifyNewPublications: false,
}
const NOTIFY_ON = {
  syncEnabled: false,
  notifyComments: true,
  notifyRecommendations: false,
  notifyNewPublications: false,
}
const ALL_OFF = {
  syncEnabled: false,
  notifyComments: false,
  notifyRecommendations: false,
  notifyNewPublications: false,
}
const USER = { id: 'u1' }

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } },
  })
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return { queryClient, wrapper }
}

/** Advance past the INITIAL_DELAY gate that fronts connect(). */
async function flushConnectDelay() {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(SSE_CONNECTION.INITIAL_DELAY)
  })
}

function lastEventSource() {
  const list = h.MockEventSource.instances
  return list[list.length - 1]
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.clearAllMocks()
  h.MockEventSource.instances = []
  h.userRef.current = null
  h.settingsRef.current = null
  useSseStore.setState({ isConnected: false, lastEventTime: null, reconnectAttempts: 0 })
  vi.spyOn(console, 'error').mockImplementation(() => {})
  vi.spyOn(console, 'warn').mockImplementation(() => {})
})

afterEach(() => {
  vi.runOnlyPendingTimers()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('useAppSse — gating truth-table', () => {
  it('connects when authenticated AND sync enabled', async () => {
    h.userRef.current = USER
    h.settingsRef.current = SYNC_ON
    const { wrapper } = createWrapper()
    renderHook(() => useAppSse(), { wrapper })

    await flushConnectDelay()
    expect(h.createEventsConnection).toHaveBeenCalledTimes(1)
  })

  it('connects when authenticated AND a notification pref is enabled', async () => {
    h.userRef.current = USER
    h.settingsRef.current = NOTIFY_ON
    const { wrapper } = createWrapper()
    renderHook(() => useAppSse(), { wrapper })

    await flushConnectDelay()
    expect(h.createEventsConnection).toHaveBeenCalledTimes(1)
  })

  it('does NOT connect when authenticated but sync + all notifications are off', async () => {
    h.userRef.current = USER
    h.settingsRef.current = ALL_OFF
    const { wrapper } = createWrapper()
    renderHook(() => useAppSse(), { wrapper })

    await flushConnectDelay()
    expect(h.createEventsConnection).not.toHaveBeenCalled()
  })

  it('does NOT connect when unauthenticated even if sync is enabled', async () => {
    h.userRef.current = null
    h.settingsRef.current = SYNC_ON
    const { wrapper } = createWrapper()
    renderHook(() => useAppSse(), { wrapper })

    await flushConnectDelay()
    expect(h.createEventsConnection).not.toHaveBeenCalled()
  })

  it('disconnects (closes the stream) when the gate flips to false', async () => {
    h.userRef.current = USER
    h.settingsRef.current = SYNC_ON
    const { wrapper } = createWrapper()
    const { rerender } = renderHook(() => useAppSse(), { wrapper })
    await flushConnectDelay()
    const es = lastEventSource()
    act(() => es.fireOpen())

    // Gate flips off → effect cleanup disconnects
    h.userRef.current = null
    act(() => rerender())

    expect(es.closed).toBe(true)
    expect(useSseStore.getState().isConnected).toBe(false)
  })
})

describe('useAppSse — event → effect map', () => {
  async function connectAndOpen() {
    h.userRef.current = USER
    h.settingsRef.current = SYNC_ON
    const { queryClient, wrapper } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    const setDataSpy = vi.spyOn(queryClient, 'setQueryData')
    renderHook(() => useAppSse(), { wrapper })
    await flushConnectDelay()
    const es = lastEventSource()
    act(() => es.fireOpen())
    invalidateSpy.mockClear()
    setDataSpy.mockClear()
    return { es, invalidateSpy, setDataSpy, queryClient }
  }

  it('planner-update patches list + detail caches and does not invalidate', async () => {
    const { es, invalidateSpy, queryClient } = await connectAndOpen()
    queryClient.setQueryData(
      ['planners', 'list'],
      [
        { id: 'p1', title: 'Old' },
        { id: 'p2', title: 'Keep' },
      ],
    )
    queryClient.setQueryData(['planners', 'detail', 'p1'], { id: 'p1', title: 'Old' })
    act(() =>
      es.emit(SSE_EVENTS.PLANNER_UPDATE, {
        type: 'updated',
        entityId: 'p1',
        payload: { id: 'p1', title: 'Changed' },
      }),
    )

    const list = queryClient.getQueryData(['planners', 'list']) as Array<{
      id: string
      title: string
    }>
    expect(list).toEqual(
      expect.arrayContaining([
        { id: 'p1', title: 'Changed' },
        { id: 'p2', title: 'Keep' },
      ]),
    )
    expect(queryClient.getQueryData(['planners', 'detail', 'p1'])).toMatchObject({
      title: 'Changed',
    })
    expect(invalidateSpy).not.toHaveBeenCalledWith({ queryKey: ['planners', 'list'] })
    expect(invalidateSpy).not.toHaveBeenCalledWith({ queryKey: ['planners', 'detail', 'p1'] })
    expect(invalidateSpy).not.toHaveBeenCalledWith({ queryKey: ['userPlanners'] })
  })

  it("'updated' planner envelope replaces the matching entry in the planner LIST cache from payload", async () => {
    const { es, queryClient } = await connectAndOpen()
    queryClient.setQueryData(
      ['planners', 'list'],
      [
        { id: 'p1', title: 'Old' },
        { id: 'p2', title: 'Keep' },
      ],
    )
    act(() =>
      es.emit(SSE_EVENTS.PLANNER_UPDATE, {
        type: 'updated',
        entityId: 'p1',
        payload: { id: 'p1', title: 'Changed' },
      }),
    )

    const list = queryClient.getQueryData(['planners', 'list']) as Array<{
      id: string
      title: string
    }>
    expect(list).toEqual(
      expect.arrayContaining([
        { id: 'p1', title: 'Changed' },
        { id: 'p2', title: 'Keep' },
      ]),
    )
    expect(list).not.toContainEqual({ id: 'p1', title: 'Old' })
  })

  it("'created' planner envelope inserts a new entry into the planner LIST cache from payload", async () => {
    const { es, queryClient } = await connectAndOpen()
    queryClient.setQueryData(['planners', 'list'], [{ id: 'p2', title: 'Keep' }])
    act(() =>
      es.emit(SSE_EVENTS.PLANNER_UPDATE, {
        type: 'created',
        entityId: 'p9',
        payload: { id: 'p9', title: 'New' },
      }),
    )

    const list = queryClient.getQueryData(['planners', 'list']) as Array<{
      id: string
      title: string
    }>
    expect(list).toEqual(
      expect.arrayContaining([
        { id: 'p9', title: 'New' },
        { id: 'p2', title: 'Keep' },
      ]),
    )
  })

  it('planner update does NOT invalidate list, detail, or userPlanners', async () => {
    const { es, invalidateSpy } = await connectAndOpen()
    act(() =>
      es.emit(SSE_EVENTS.PLANNER_UPDATE, {
        type: 'updated',
        entityId: 'p1',
        payload: { id: 'p1', title: 'Changed' },
      }),
    )

    expect(invalidateSpy).not.toHaveBeenCalledWith({ queryKey: ['planners', 'list'] })
    expect(invalidateSpy).not.toHaveBeenCalledWith({ queryKey: ['planners', 'detail', 'p1'] })
    expect(invalidateSpy).not.toHaveBeenCalledWith({ queryKey: ['userPlanners'] })
  })

  it("'updated' planner envelope patches the detail cache from payload and does not invalidate", async () => {
    const { es, invalidateSpy, queryClient } = await connectAndOpen()
    queryClient.setQueryData(['planners', 'detail', 'p1'], { id: 'p1', title: 'Old Title' })
    const envelope = {
      type: 'updated',
      entityId: 'p1',
      payload: { id: 'p1', title: 'Changed Title' },
    }

    act(() => es.emit(SSE_EVENTS.PLANNER_UPDATE, envelope))

    expect(queryClient.getQueryData(['planners', 'detail', 'p1'])).toMatchObject({
      title: 'Changed Title',
    })
    expect(invalidateSpy).not.toHaveBeenCalledWith({ queryKey: ['planners', 'detail', 'p1'] })
  })

  it('planner-update of type "deleted" purges the local planner copy', async () => {
    const { es } = await connectAndOpen()
    act(() => es.emit(SSE_EVENTS.PLANNER_UPDATE, { type: 'deleted', deletedId: 'p1' }))

    expect(h.deleteFromLocal).toHaveBeenCalledWith('p1')
  })

  it("'deleted' planner envelope removes the entry from the LIST cache and does not invalidate", async () => {
    const { es, invalidateSpy, queryClient } = await connectAndOpen()
    queryClient.setQueryData(
      ['planners', 'list'],
      [
        { id: 'p1', title: 'A' },
        { id: 'p2', title: 'B' },
      ],
    )
    act(() => es.emit(SSE_EVENTS.PLANNER_UPDATE, { type: 'deleted', deletedId: 'p1' }))

    const list = queryClient.getQueryData(['planners', 'list']) as Array<{
      id: string
      title: string
    }>
    expect(list).not.toContainEqual(expect.objectContaining({ id: 'p1' }))
    expect(list).toContainEqual({ id: 'p2', title: 'B' })
    expect(invalidateSpy).not.toHaveBeenCalledWith({ queryKey: ['planners', 'list'] })
  })

  it('notification event invalidates notification caches and shows a toast when tab visible', async () => {
    h.isTabHidden.mockReturnValue(false)
    const { es, invalidateSpy } = await connectAndOpen()
    act(() =>
      es.emit(SSE_EVENTS.NOTIFY_COMMENT, {
        type: 'COMMENT_RECEIVED',
        plannerId: 'p1',
        plannerTitle: 'My Plan',
      }),
    )

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['notifications'] })
    expect(h.showNotificationToast).toHaveBeenCalledTimes(1)
    expect(h.showBrowserNotification).not.toHaveBeenCalled()
  })

  it("'notify:published' envelope inserts the published planner into the LIST cache from payload and does not invalidate the list, still toasts", async () => {
    h.isTabHidden.mockReturnValue(false)
    const { es, invalidateSpy, queryClient } = await connectAndOpen()
    queryClient.setQueryData(['planners', 'list'], [{ id: 'p2', title: 'B' }])
    act(() =>
      es.emit(SSE_EVENTS.NOTIFY_PUBLISHED, {
        type: 'notify:published',
        entityId: 'p9',
        payload: { id: 'p9', title: 'Published' },
        plannerId: 'p9',
        plannerTitle: 'Published',
        authorKeyword: 'auth',
        authorSuffix: '0001',
      }),
    )

    const list = queryClient.getQueryData(['planners', 'list']) as Array<{
      id: string
      title: string
    }>
    expect(list).toEqual(
      expect.arrayContaining([
        { id: 'p9', title: 'Published' },
        { id: 'p2', title: 'B' },
      ]),
    )
    expect(invalidateSpy).not.toHaveBeenCalledWith({ queryKey: ['planners', 'list'] })
    expect(h.showNotificationToast).toHaveBeenCalled()
  })
})

describe('useAppSse — unmount cleanup', () => {
  it('closes the stream on unmount', async () => {
    h.userRef.current = USER
    h.settingsRef.current = SYNC_ON
    const { wrapper } = createWrapper()
    const { unmount } = renderHook(() => useAppSse(), { wrapper })
    await flushConnectDelay()
    const es = lastEventSource()
    act(() => es.fireOpen())

    act(() => unmount())
    expect(es.closed).toBe(true)
  })

  it('does not open a new stream after unmount', async () => {
    h.userRef.current = USER
    h.settingsRef.current = SYNC_ON
    const { wrapper } = createWrapper()
    const { unmount } = renderHook(() => useAppSse(), { wrapper })
    await flushConnectDelay()
    act(() => unmount())
    h.createEventsConnection.mockClear()

    // Drain any scheduled timers; none should reconnect a torn-down hook.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(SSE_CONNECTION.MAX_DELAY)
    })
    expect(h.createEventsConnection).not.toHaveBeenCalled()
  })
})
