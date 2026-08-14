import { renderHook, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

import { SSE_CONNECTION, SSE_EVENTS, SSE_TRANSPORT } from '@/lib/constants'
import { useSseStore } from '../../stores/useSseStore'
import { useSseEngine, type SseEngineConfig } from '../useSseEngine'

/**
 * DI tests for the generic SSE engine. The only double is `fetch`: it answers
 * with a scripted `Response` whose body is a ReadableStream the test feeds
 * chunk by chunk, so the tangled transport logic — frame decoding, backoff,
 * idle reset, proactive reconnect, MAX_ATTEMPTS recovery, 429 cooldown,
 * cleanup — is exercised with zero module mocks.
 */

const STREAM_PATH = '/api/sse/subscribe'
const BASE_URL = 'http://localhost:8080'
const RETRY_AFTER_SECONDS = 30
const encoder = new TextEncoder()

interface StreamHandle {
  push: (chunk: string) => void
  end: () => void
  cancelled: boolean
  signal: AbortSignal | null
}

const streams: StreamHandle[] = []
const requests: RequestInit[] = []
const requestUrls: string[] = []
const onceQueue: ((init: RequestInit) => Response)[] = []

/** A 200 whose body stays open until the test ends it. */
function streamResponse(init: RequestInit): Response {
  let controller: ReadableStreamDefaultController<Uint8Array> | null = null
  const handle: StreamHandle = {
    push: (chunk) => controller?.enqueue(encoder.encode(chunk)),
    end: () => controller?.close(),
    cancelled: false,
    signal: init.signal ?? null,
  }
  const body = new ReadableStream<Uint8Array>({
    start(c) {
      controller = c
    },
    cancel() {
      handle.cancelled = true
    },
  })
  streams.push(handle)
  return { ok: true, status: 200, headers: new Headers(), body } as unknown as Response
}

function statusResponse(status: number, headers: Record<string, string> = {}) {
  return () =>
    ({ ok: false, status, headers: new Headers(headers), body: null }) as unknown as Response
}

const rateLimited = (seconds: number) =>
  statusResponse(SSE_TRANSPORT.RATE_LIMITED_STATUS, {
    [SSE_TRANSPORT.RETRY_AFTER_HEADER]: String(seconds),
  })

/** Answer the next connection attempt with `responder` instead of a stream. */
function serveOnce(responder: (init: RequestInit) => Response) {
  onceQueue.push(responder)
}

/** Answer every connection attempt with `responder`. */
let defaultResponder: (init: RequestInit) => Response = streamResponse
function serveAlways(responder: (init: RequestInit) => Response) {
  defaultResponder = responder
}

const lastStream = () => streams[streams.length - 1]

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

function emit(handle: StreamHandle, type: string, data: unknown) {
  handle.push(`event: ${type}\ndata: ${JSON.stringify(data)}\n\n`)
}

/** Stable config refs so the engine effect only re-runs on `shouldConnect`. */
function makeConfig(overrides: Partial<SseEngineConfig> = {}): SseEngineConfig {
  return {
    shouldConnect: true,
    url: STREAM_PATH,
    handlers: {},
    ...overrides,
  }
}

beforeEach(() => {
  vi.useFakeTimers()
  streams.length = 0
  requests.length = 0
  requestUrls.length = 0
  onceQueue.length = 0
  defaultResponder = streamResponse
  useSseStore.setState({ isConnected: false, lastEventTime: null, reconnectAttempts: 0 })
  vi.spyOn(console, 'warn').mockImplementation(() => {})
  vi.spyOn(Math, 'random').mockReturnValue(0)
  vi.mocked(globalThis.fetch).mockImplementation((input: unknown, init: RequestInit = {}) => {
    requestUrls.push(String(input))
    requests.push(init)
    const responder = onceQueue.shift() ?? defaultResponder
    return Promise.resolve(responder(init))
  })
})

afterEach(() => {
  vi.runOnlyPendingTimers()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('useSseEngine — gating', () => {
  it('connects after INITIAL_DELAY when shouldConnect is true', async () => {
    renderHook(() => useSseEngine(makeConfig()))
    expect(globalThis.fetch).not.toHaveBeenCalled()
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
  })

  it('opens the stream with credentials and the event-stream Accept header', async () => {
    renderHook(() => useSseEngine(makeConfig()))
    await advance(SSE_CONNECTION.INITIAL_DELAY)

    expect(requestUrls[0]).toBe(`${BASE_URL}${STREAM_PATH}`)
    expect(requests[0].credentials).toBe('include')
    expect(requests[0].headers).toMatchObject({ Accept: SSE_TRANSPORT.ACCEPT })
  })

  it('does not connect when shouldConnect is false', async () => {
    renderHook(() => useSseEngine(makeConfig({ shouldConnect: false })))
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    expect(globalThis.fetch).not.toHaveBeenCalled()
  })

  it('aborts the request and marks disconnected when the gate flips to false', async () => {
    const config = makeConfig()
    const { rerender } = renderHook((p: SseEngineConfig) => useSseEngine(p), {
      initialProps: config,
    })
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    expect(useSseStore.getState().isConnected).toBe(true)
    const stream = lastStream()

    rerender({ ...config, shouldConnect: false })
    await settle()

    expect(stream.signal?.aborted).toBe(true)
    expect(useSseStore.getState().isConnected).toBe(false)
  })
})

describe('useSseEngine — connection lifecycle', () => {
  it('opening marks connected but does NOT reset attempts on open alone', async () => {
    useSseStore.setState({ reconnectAttempts: 3 })
    renderHook(() => useSseEngine(makeConfig()))
    await advance(SSE_CONNECTION.INITIAL_DELAY)

    expect(useSseStore.getState().isConnected).toBe(true)
    // Opening is not proof of health: a stream that opens then immediately drops
    // must keep the backoff climbing, so the reset waits for a stable duration.
    expect(useSseStore.getState().reconnectAttempts).toBe(3)
  })

  it('dispatches injected handlers for their event type', async () => {
    const onUpdate = vi.fn()
    renderHook(() =>
      useSseEngine(makeConfig({ handlers: { [SSE_EVENTS.COMMENT_ADDED]: onUpdate } })),
    )
    await advance(SSE_CONNECTION.INITIAL_DELAY)

    emit(lastStream(), SSE_EVENTS.COMMENT_ADDED, { plannerId: 'p1' })
    await settle()

    expect(onUpdate).toHaveBeenCalledTimes(1)
    expect(JSON.parse(onUpdate.mock.calls[0][0].data)).toEqual({ plannerId: 'p1' })
  })

  it('assembles a frame split across chunk boundaries', async () => {
    const onUpdate = vi.fn()
    renderHook(() =>
      useSseEngine(makeConfig({ handlers: { [SSE_EVENTS.COMMENT_ADDED]: onUpdate } })),
    )
    await advance(SSE_CONNECTION.INITIAL_DELAY)

    const stream = lastStream()
    stream.push(`event: ${SSE_EVENTS.COMMENT_ADDED}\ndata: {"plan`)
    await settle()
    expect(onUpdate).not.toHaveBeenCalled()

    stream.push('nerId":"p1"}\n')
    await settle()
    expect(onUpdate).not.toHaveBeenCalled()

    stream.push('\n')
    await settle()
    expect(onUpdate).toHaveBeenCalledTimes(1)
    expect(JSON.parse(onUpdate.mock.calls[0][0].data)).toEqual({ plannerId: 'p1' })
  })

  it('joins multiple data lines with a newline and ignores comment lines', async () => {
    const onUpdate = vi.fn()
    renderHook(() =>
      useSseEngine(makeConfig({ handlers: { [SSE_EVENTS.COMMENT_ADDED]: onUpdate } })),
    )
    await advance(SSE_CONNECTION.INITIAL_DELAY)

    lastStream().push(`:keep-alive\nevent: ${SSE_EVENTS.COMMENT_ADDED}\ndata: one\ndata: two\n\n`)
    await settle()

    expect(onUpdate).toHaveBeenCalledTimes(1)
    expect(onUpdate.mock.calls[0][0].data).toBe('one\ntwo')
  })

  it('drops a frame whose type is outside the vocabulary', async () => {
    // The gate is the engine's, so every consumer inherits it — a server that
    // starts naming an event the client does not know reaches no handler.
    const onUpdate = vi.fn()
    renderHook(() =>
      useSseEngine(makeConfig({ handlers: { [SSE_EVENTS.COMMENT_ADDED]: onUpdate } })),
    )
    await advance(SSE_CONNECTION.INITIAL_DELAY)

    emit(lastStream(), 'renamed', { plannerId: 'p1' })
    await settle()

    expect(onUpdate).not.toHaveBeenCalled()
  })

  it('marks connected on the transport-level connected event', async () => {
    renderHook(() => useSseEngine(makeConfig()))
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    useSseStore.setState({ isConnected: false })

    lastStream().push(`event: ${SSE_EVENTS.CONNECTED}\ndata: ok\n\n`)
    await settle()

    expect(useSseStore.getState().isConnected).toBe(true)
  })

  it('aborts the request and stops reconnecting on unmount', async () => {
    const { unmount } = renderHook(() => useSseEngine(makeConfig()))
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    const stream = lastStream()

    act(() => unmount())
    await settle()
    expect(stream.signal?.aborted).toBe(true)
    expect(stream.cancelled).toBe(true)

    vi.mocked(globalThis.fetch).mockClear()
    await advance(SSE_CONNECTION.PROACTIVE_RECONNECT_INTERVAL + SSE_CONNECTION.INITIAL_DELAY)
    expect(globalThis.fetch).not.toHaveBeenCalled()
  })
})

describe('useSseEngine — backoff + reconnect', () => {
  it('reconnects after BASE_DELAY when the stream ends', async () => {
    renderHook(() => useSseEngine(makeConfig()))
    await advance(SSE_CONNECTION.INITIAL_DELAY)

    lastStream().end()
    await settle()
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)

    await advance(SSE_CONNECTION.BASE_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
  })

  it('applies exponential backoff to a stream that never opens', async () => {
    serveAlways(statusResponse(500))
    renderHook(() => useSseEngine(makeConfig()))

    // First failure → reconnect after BASE_DELAY.
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
    await advance(SSE_CONNECTION.BASE_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(2)

    // Second failure → the next reconnect must wait 2× BASE_DELAY, not 1×.
    await advance(SSE_CONNECTION.BASE_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
    await advance(SSE_CONNECTION.BASE_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(3)
  })

  it('keeps backing off when connections open but die before the stability floor', async () => {
    // The storm regression: crediting an open as healthy let an
    // open-then-immediately-drop cycle reconnect at BASE_DELAY forever.
    renderHook(() => useSseEngine(makeConfig()))
    await advance(SSE_CONNECTION.INITIAL_DELAY)

    lastStream().end()
    await settle()
    await advance(SSE_CONNECTION.BASE_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(2)

    lastStream().end()
    await settle()
    await advance(SSE_CONNECTION.BASE_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
    await advance(SSE_CONNECTION.BASE_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(3)
  })

  it('resets backoff after a connection stays open past the stability floor', async () => {
    useSseStore.setState({ reconnectAttempts: 5 })
    renderHook(() => useSseEngine(makeConfig()))
    await advance(SSE_CONNECTION.INITIAL_DELAY)

    // Stay open past the floor, then drop → healthy connection, counter resets, so
    // the next reconnect uses BASE_DELAY rather than the elevated (5th-attempt) delay.
    await advance(SSE_CONNECTION.STABLE_CONNECTION_THRESHOLD)
    lastStream().end()
    await settle()
    expect(useSseStore.getState().reconnectAttempts).toBe(1)

    await advance(SSE_CONNECTION.BASE_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
  })

  it('adds 0–5s jitter on top of the backoff delay', async () => {
    vi.spyOn(Math, 'random').mockReturnValue(0.5)
    renderHook(() => useSseEngine(makeConfig()))
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    lastStream().end()
    await settle()

    // BASE_DELAY alone must NOT trigger the reconnect — jitter (0.5 × 5000 = 2500ms) is still pending.
    await advance(SSE_CONNECTION.BASE_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)

    await advance(SSE_CONNECTION.MAX_JITTER / 2)
    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
  })

  it('recovers via idle-reset after MAX_ATTEMPTS instead of dying permanently', async () => {
    useSseStore.setState({ reconnectAttempts: SSE_CONNECTION.MAX_ATTEMPTS - 1 })
    serveAlways(statusResponse(500))
    renderHook(() => useSseEngine(makeConfig()))

    // The first failure pushes attempts to MAX and schedules the final reconnect.
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
    await advance(SSE_CONNECTION.MAX_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(2)

    // That attempt hits the MAX_ATTEMPTS ceiling → gives up, schedules nothing.
    await advance(SSE_CONNECTION.MAX_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(2)

    // The pending idle-reset fires → clears the counter AND re-arms connect.
    serveAlways(streamResponse)
    await advance(SSE_CONNECTION.IDLE_RESET_TIMEOUT - 2 * SSE_CONNECTION.MAX_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(3)
    expect(useSseStore.getState().isConnected).toBe(true)
    expect(useSseStore.getState().reconnectAttempts).toBe(0)
  })

  it('never reconnects sooner than the retry interval the stream declared', async () => {
    const serverRetryMs = 20_000
    renderHook(() => useSseEngine(makeConfig()))
    await advance(SSE_CONNECTION.INITIAL_DELAY)

    lastStream().push(`retry: ${serverRetryMs}\n\n`)
    await settle()
    lastStream().end()
    await settle()

    await advance(serverRetryMs - 1)
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)

    await advance(1)
    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
  })

  it('proactively reconnects before token expiry', async () => {
    renderHook(() => useSseEngine(makeConfig()))
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    const first = lastStream()

    await advance(SSE_CONNECTION.PROACTIVE_RECONNECT_INTERVAL)
    expect(first.signal?.aborted).toBe(true)
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
  })
})

describe('useSseEngine — stream gone', () => {
  it('reports a 404 open exactly once and never reconnects when opted in', async () => {
    serveAlways(statusResponse(SSE_TRANSPORT.STREAM_GONE_STATUS))
    const onStreamGone = vi.fn()
    renderHook(() => useSseEngine(makeConfig({ stopOnNotFound: true, onStreamGone })))

    await advance(SSE_CONNECTION.INITIAL_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
    expect(onStreamGone).toHaveBeenCalledTimes(1)

    // Nothing the backoff, the idle reset, or the proactive timer could fire
    // may reopen a stream the server said is gone.
    await advance(SSE_CONNECTION.IDLE_RESET_TIMEOUT + SSE_CONNECTION.MAX_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
    expect(onStreamGone).toHaveBeenCalledTimes(1)
    expect(useSseStore.getState().isConnected).toBe(false)
  })

  it('spends no reconnect attempt on a 404 when opted in', async () => {
    serveAlways(statusResponse(SSE_TRANSPORT.STREAM_GONE_STATUS))
    renderHook(() => useSseEngine(makeConfig({ stopOnNotFound: true })))

    await advance(SSE_CONNECTION.INITIAL_DELAY)
    await advance(SSE_CONNECTION.IDLE_RESET_TIMEOUT)

    expect(useSseStore.getState().reconnectAttempts).toBe(0)
  })

  it('keeps the backoff on a 404 without the opt-in', async () => {
    // A path that names no resource answers 404 for a routing miss or a deploy
    // window; stopping there would kill the stream for the whole session.
    serveAlways(statusResponse(SSE_TRANSPORT.STREAM_GONE_STATUS))
    const onStreamGone = vi.fn()
    renderHook(() => useSseEngine(makeConfig({ onStreamGone })))

    await advance(SSE_CONNECTION.INITIAL_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)

    await advance(SSE_CONNECTION.BASE_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
    expect(onStreamGone).not.toHaveBeenCalled()
  })

  it('still reconnects when the open fails with a status that is not 404', async () => {
    serveAlways(statusResponse(500))
    const onStreamGone = vi.fn()
    renderHook(() => useSseEngine(makeConfig({ stopOnNotFound: true, onStreamGone })))

    await advance(SSE_CONNECTION.INITIAL_DELAY)
    await advance(SSE_CONNECTION.BASE_DELAY)

    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
    expect(onStreamGone).not.toHaveBeenCalled()
  })
})

describe('useSseEngine — rate limiting', () => {
  it('waits out Retry-After without spending an attempt', async () => {
    serveOnce(rateLimited(RETRY_AFTER_SECONDS))
    renderHook(() => useSseEngine(makeConfig()))
    await advance(SSE_CONNECTION.INITIAL_DELAY)

    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
    expect(useSseStore.getState().reconnectAttempts).toBe(0)

    const retryAfterMs = RETRY_AFTER_SECONDS * SSE_TRANSPORT.RETRY_AFTER_UNIT_MS
    await advance(retryAfterMs - 1)
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)

    await advance(1)
    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
    expect(useSseStore.getState().reconnectAttempts).toBe(0)
  })

  it('never retries a 429 sooner than the slowest backoff tier', async () => {
    serveOnce(rateLimited(1))
    renderHook(() => useSseEngine(makeConfig()))
    await advance(SSE_CONNECTION.INITIAL_DELAY)

    await advance(SSE_CONNECTION.MAX_DELAY - 1)
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)

    await advance(1)
    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
  })

  it('falls back to the slowest tier when the 429 carries no Retry-After', async () => {
    serveOnce(statusResponse(SSE_TRANSPORT.RATE_LIMITED_STATUS))
    renderHook(() => useSseEngine(makeConfig()))
    await advance(SSE_CONNECTION.INITIAL_DELAY)

    await advance(SSE_CONNECTION.MAX_DELAY)
    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
    expect(useSseStore.getState().reconnectAttempts).toBe(0)
  })
})
