import { renderHook, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

import { SSE_CONNECTION, SSE_EVENTS } from '@/lib/constants'
import { useSseStore } from '@/shared/sse'

/**
 * Characterization tests for useAppSse — the SSE orchestration hook, exercising
 * the REAL generic engine (@/shared/sse) end-to-end.
 *
 * Scope (deliberately narrow): the four seams the shared→pages split endangers —
 * (a) the connection gating truth-table (auth × settings), (b) the event→effect
 * map (which SSE event drives which notification side-effect, and that planner
 * events drive none), (c) the reconnect catch-up, and (d) unmount cleanup.
 * Reconnect/backoff timing lives in the engine's own DI tests, not here.
 *
 * Only leaf deps are mocked: auth, settings and notifications by their public
 * specifiers. The transport double is `fetch` itself: it answers the stream
 * request with a body the test feeds frames into.
 */

const encoder = new TextEncoder()

interface StreamHandle {
  push: (chunk: string) => void
  end: () => void
  signal: AbortSignal | null
}

const streams: StreamHandle[] = []

// ── Mutable auth/settings refs shared with the module mocks ──
const h = vi.hoisted(() => ({
  userRef: { current: null as unknown },
  settingsRef: { current: null as unknown },
  showBrowserNotification: vi.fn(),
  showNotificationToast: vi.fn(),
  isTabHidden: vi.fn(() => false),
  NOTIFICATION_KEYS: { all: ['notifications'] as const },
  SETTINGS_KEYS: { settings: () => ['user', 'settings'] as const },
}))

vi.mock('@/shared/userSettings', () => ({
  useUserSettingsQuery: () => ({ data: h.settingsRef.current, isLoading: false }),
  userSettingsKeys: h.SETTINGS_KEYS,
}))

vi.mock('@/shared/auth', () => ({
  useAuthQueryNonBlocking: () => ({ data: h.userRef.current }),
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

/** Flush the promise chain the fetch transport runs on. */
async function settle() {
  await act(async () => {
    for (let i = 0; i < 20; i++) {
      await Promise.resolve()
    }
  })
}

async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms)
  })
  await settle()
}

/** Advance past the INITIAL_DELAY gate that fronts connect(). */
async function flushConnectDelay() {
  await advance(SSE_CONNECTION.INITIAL_DELAY)
}

function lastStream() {
  return streams[streams.length - 1]
}

async function emit(stream: StreamHandle, type: string, data: unknown) {
  stream.push(`event: ${type}\ndata: ${JSON.stringify(data)}\n\n`)
  await settle()
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.clearAllMocks()
  streams.length = 0
  h.userRef.current = null
  h.settingsRef.current = null
  useSseStore.setState({ isConnected: false, lastEventTime: null, reconnectAttempts: 0 })
  vi.spyOn(console, 'error').mockImplementation(() => {})
  vi.spyOn(console, 'warn').mockImplementation(() => {})
  vi.mocked(globalThis.fetch).mockImplementation((_input: unknown, init: RequestInit = {}) => {
    let controller: ReadableStreamDefaultController<Uint8Array> | null = null
    const body = new ReadableStream<Uint8Array>({
      start(c) {
        controller = c
      },
    })
    streams.push({
      push: (chunk) => controller?.enqueue(encoder.encode(chunk)),
      end: () => controller?.close(),
      signal: init.signal ?? null,
    })
    return Promise.resolve({
      ok: true,
      status: 200,
      headers: new Headers(),
      body,
    } as unknown as Response)
  })
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
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
  })

  it('connects when authenticated AND a notification pref is enabled', async () => {
    h.userRef.current = USER
    h.settingsRef.current = NOTIFY_ON
    const { wrapper } = createWrapper()
    renderHook(() => useAppSse(), { wrapper })

    await flushConnectDelay()
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
  })

  it('does NOT connect when authenticated but sync + all notifications are off', async () => {
    h.userRef.current = USER
    h.settingsRef.current = ALL_OFF
    const { wrapper } = createWrapper()
    renderHook(() => useAppSse(), { wrapper })

    await flushConnectDelay()
    expect(globalThis.fetch).not.toHaveBeenCalled()
  })

  it('does NOT connect when unauthenticated even if sync is enabled', async () => {
    h.userRef.current = null
    h.settingsRef.current = SYNC_ON
    const { wrapper } = createWrapper()
    renderHook(() => useAppSse(), { wrapper })

    await flushConnectDelay()
    expect(globalThis.fetch).not.toHaveBeenCalled()
  })

  it('disconnects (aborts the stream) when the gate flips to false', async () => {
    h.userRef.current = USER
    h.settingsRef.current = SYNC_ON
    const { wrapper } = createWrapper()
    const { rerender } = renderHook(() => useAppSse(), { wrapper })
    await flushConnectDelay()
    const stream = lastStream()

    // Gate flips off → effect cleanup disconnects
    h.userRef.current = null
    act(() => rerender())
    await settle()

    expect(stream.signal?.aborted).toBe(true)
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
    const stream = lastStream()
    invalidateSpy.mockClear()
    setDataSpy.mockClear()
    return { stream, invalidateSpy, setDataSpy, queryClient }
  }

  it('notification event invalidates notification caches and shows a toast when tab visible', async () => {
    h.isTabHidden.mockReturnValue(false)
    const { stream, invalidateSpy } = await connectAndOpen()
    await emit(stream, SSE_EVENTS.NOTIFY_COMMENT, {
      type: 'COMMENT_RECEIVED',
      plannerId: 'p1',
      plannerTitle: 'My Plan',
    })

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['notifications'] })
    expect(h.showNotificationToast).toHaveBeenCalledTimes(1)
    expect(h.showBrowserNotification).not.toHaveBeenCalled()
  })

  it("'notify:published' arrives as a bare payload: it toasts and leaves the personal list alone", async () => {
    h.isTabHidden.mockReturnValue(false)
    const { stream, invalidateSpy, queryClient } = await connectAndOpen()
    queryClient.setQueryData(['planners', 'list'], [{ id: 'p2', title: 'B' }])
    await emit(stream, SSE_EVENTS.NOTIFY_PUBLISHED, {
      plannerId: 'p9',
      plannerTitle: 'Published',
      authorEpithet: 'NAIVE',
      authorSuffix: '0001',
    })

    expect(queryClient.getQueryData(['planners', 'list'])).toEqual([{ id: 'p2', title: 'B' }])
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['notifications'] })
    expect(h.showNotificationToast).toHaveBeenCalled()
  })

  it('an unparseable payload on a handled type raises no notification and does not throw', async () => {
    const { stream, invalidateSpy } = await connectAndOpen()

    stream.push(`event: ${SSE_EVENTS.NOTIFY_COMMENT}\ndata: not json at all\n\n`)
    await settle()

    // The handler invalidates before it parses, so the cache refresh still runs;
    // what the unparseable body must not do is surface a notification.
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['notifications'] })
    expect(h.showNotificationToast).not.toHaveBeenCalled()
    expect(h.showBrowserNotification).not.toHaveBeenCalled()
  })

  it('a frame whose type is outside the vocabulary reaches no handler', async () => {
    const { stream, invalidateSpy, setDataSpy } = await connectAndOpen()

    await emit(stream, 'renamed', { entityId: 'p1' })

    expect(setDataSpy).not.toHaveBeenCalled()
    expect(invalidateSpy).not.toHaveBeenCalled()
    expect(h.showNotificationToast).not.toHaveBeenCalled()
  })
})

describe('useAppSse — reconnect catch-up', () => {
  it('does not invalidate settings on the first connection of a session', async () => {
    h.userRef.current = USER
    h.settingsRef.current = SYNC_ON
    const { queryClient, wrapper } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    renderHook(() => useAppSse(), { wrapper })

    await flushConnectDelay()

    expect(invalidateSpy).not.toHaveBeenCalledWith({ queryKey: ['user', 'settings'] })
  })

  it('invalidates settings once the stream comes back after a drop', async () => {
    h.userRef.current = USER
    h.settingsRef.current = SYNC_ON
    const { queryClient, wrapper } = createWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    renderHook(() => useAppSse(), { wrapper })
    await flushConnectDelay()

    lastStream().end()
    await settle()
    await advance(SSE_CONNECTION.BASE_DELAY + SSE_CONNECTION.MAX_JITTER)

    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user', 'settings'] })
  })
})

describe('useAppSse — unmount cleanup', () => {
  it('aborts the stream on unmount', async () => {
    h.userRef.current = USER
    h.settingsRef.current = SYNC_ON
    const { wrapper } = createWrapper()
    const { unmount } = renderHook(() => useAppSse(), { wrapper })
    await flushConnectDelay()
    const stream = lastStream()

    act(() => unmount())
    await settle()
    expect(stream.signal?.aborted).toBe(true)
  })

  it('does not open a new stream after unmount', async () => {
    h.userRef.current = USER
    h.settingsRef.current = SYNC_ON
    const { wrapper } = createWrapper()
    const { unmount } = renderHook(() => useAppSse(), { wrapper })
    await flushConnectDelay()
    act(() => unmount())
    vi.mocked(globalThis.fetch).mockClear()

    // Drain any scheduled timers; none should reconnect a torn-down hook.
    await advance(SSE_CONNECTION.MAX_DELAY)
    expect(globalThis.fetch).not.toHaveBeenCalled()
  })
})
