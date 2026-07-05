import { renderHook, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

import { SSE_CONNECTION, SSE_EVENTS } from '@/lib/constants'
import { useSseStore } from '../../stores/useSseStore'
import { useSseEngine, type SseEngineConfig } from '../useSseEngine'

/**
 * DI tests for the generic SSE engine. The engine is pure dependency-injection
 * (fake `createConnection` + a controllable EventSource double), so the tangled
 * timer logic — backoff, idle reset, proactive reconnect, MAX_ATTEMPTS recovery,
 * cleanup — is exercised here with zero module mocks.
 */

class MockEventSource {
  static instances: MockEventSource[] = []
  onopen: (() => void) | null = null
  onerror: (() => void) | null = null
  listeners: Record<string, ((e: MessageEvent) => void)[]> = {}
  closed = false
  constructor(public url: string) {
    MockEventSource.instances.push(this)
  }
  addEventListener(type: string, cb: (e: MessageEvent) => void) {
    ;(this.listeners[type] ??= []).push(cb)
  }
  close() {
    this.closed = true
  }
  fireOpen() {
    this.onopen?.()
  }
  fireError() {
    this.onerror?.()
  }
  emit(type: string, data: unknown) {
    ;(this.listeners[type] ?? []).forEach((cb) =>
      cb({ data: JSON.stringify(data) } as MessageEvent),
    )
  }
}

const last = () => MockEventSource.instances[MockEventSource.instances.length - 1]

async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms)
  })
}

/** Stable config refs so the engine effect only re-runs on `shouldConnect`. */
function makeConfig(overrides: Partial<SseEngineConfig> = {}): SseEngineConfig {
  return {
    shouldConnect: true,
    createConnection: vi.fn(() => new MockEventSource('/sse')),
    handlers: {},
    ...overrides,
  }
}

beforeEach(() => {
  vi.useFakeTimers()
  MockEventSource.instances = []
  useSseStore.setState({ isConnected: false, lastEventTime: null, reconnectAttempts: 0 })
  vi.spyOn(console, 'warn').mockImplementation(() => {})
})

afterEach(() => {
  vi.runOnlyPendingTimers()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('useSseEngine — gating', () => {
  it('connects after INITIAL_DELAY when shouldConnect is true', async () => {
    const config = makeConfig()
    renderHook(() => useSseEngine(config))
    expect(config.createConnection).not.toHaveBeenCalled()
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    expect(config.createConnection).toHaveBeenCalledTimes(1)
  })

  it('does not connect when shouldConnect is false', async () => {
    const config = makeConfig({ shouldConnect: false })
    renderHook(() => useSseEngine(config))
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    expect(config.createConnection).not.toHaveBeenCalled()
  })

  it('disconnects and marks disconnected when the gate flips to false', async () => {
    const config = makeConfig()
    const { rerender } = renderHook((p: SseEngineConfig) => useSseEngine(p), {
      initialProps: config,
    })
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    const es = last()
    act(() => es.fireOpen())
    expect(useSseStore.getState().isConnected).toBe(true)

    rerender({ ...config, shouldConnect: false })
    expect(es.closed).toBe(true)
    expect(useSseStore.getState().isConnected).toBe(false)
  })
})

describe('useSseEngine — connection lifecycle', () => {
  it('onopen marks connected and resets reconnect attempts', async () => {
    useSseStore.setState({ reconnectAttempts: 3 })
    const config = makeConfig()
    renderHook(() => useSseEngine(config))
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    act(() => last().fireOpen())
    expect(useSseStore.getState().isConnected).toBe(true)
    expect(useSseStore.getState().reconnectAttempts).toBe(0)
  })

  it('dispatches injected handlers for their event type', async () => {
    const onUpdate = vi.fn()
    const config = makeConfig({ handlers: { [SSE_EVENTS.PLANNER_UPDATE]: onUpdate } })
    renderHook(() => useSseEngine(config))
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    act(() => last().fireOpen())
    act(() => last().emit(SSE_EVENTS.PLANNER_UPDATE, { plannerId: 'p1' }))
    expect(onUpdate).toHaveBeenCalledTimes(1)
  })

  it('closes the stream and stops reconnecting on unmount', async () => {
    const config = makeConfig()
    const { unmount } = renderHook(() => useSseEngine(config))
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    const es = last()
    act(() => es.fireOpen())

    act(() => unmount())
    expect(es.closed).toBe(true)
    ;(config.createConnection as ReturnType<typeof vi.fn>).mockClear()
    await advance(SSE_CONNECTION.PROACTIVE_RECONNECT_INTERVAL + SSE_CONNECTION.INITIAL_DELAY)
    expect(config.createConnection).not.toHaveBeenCalled()
  })
})

describe('useSseEngine — backoff + reconnect', () => {
  it('reconnects after BASE_DELAY on the first error', async () => {
    const config = makeConfig()
    renderHook(() => useSseEngine(config))
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    act(() => last().fireOpen())
    act(() => last().fireError())

    expect(config.createConnection).toHaveBeenCalledTimes(1)
    await advance(SSE_CONNECTION.BASE_DELAY)
    expect(config.createConnection).toHaveBeenCalledTimes(2)
  })

  it('applies exponential backoff — the second error waits ~2× BASE_DELAY', async () => {
    const config = makeConfig()
    renderHook(() => useSseEngine(config))
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    act(() => last().fireOpen())

    // First error → reconnect after BASE_DELAY.
    act(() => last().fireError())
    await advance(SSE_CONNECTION.BASE_DELAY)
    expect(config.createConnection).toHaveBeenCalledTimes(2)

    // Second error → next reconnect must wait 2× BASE_DELAY, not 1×.
    act(() => last().fireError())
    await advance(SSE_CONNECTION.BASE_DELAY)
    expect(config.createConnection).toHaveBeenCalledTimes(2)
    await advance(SSE_CONNECTION.BASE_DELAY)
    expect(config.createConnection).toHaveBeenCalledTimes(3)
  })

  it('recovers via idle-reset after MAX_ATTEMPTS instead of dying permanently', async () => {
    useSseStore.setState({ reconnectAttempts: SSE_CONNECTION.MAX_ATTEMPTS - 1 })
    const config = makeConfig()
    renderHook(() => useSseEngine(config))
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    expect(config.createConnection).toHaveBeenCalledTimes(1)

    // Error pushes attempts to MAX and schedules the final reconnect (+ idle timer).
    act(() => last().fireError())
    await advance(SSE_CONNECTION.MAX_DELAY)
    expect(config.createConnection).toHaveBeenCalledTimes(2)

    // Next error hits the MAX_ATTEMPTS ceiling → gives up, schedules nothing.
    act(() => last().fireError())
    await advance(SSE_CONNECTION.MAX_DELAY)
    expect(config.createConnection).toHaveBeenCalledTimes(2)

    // The pending idle-reset fires → clears the counter AND re-arms connect.
    await advance(SSE_CONNECTION.IDLE_RESET_TIMEOUT)
    expect(config.createConnection).toHaveBeenCalledTimes(3)
    expect(useSseStore.getState().reconnectAttempts).toBe(0)
  })

  it('proactively reconnects before token expiry', async () => {
    const config = makeConfig()
    renderHook(() => useSseEngine(config))
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    const first = last()
    act(() => first.fireOpen())

    await advance(SSE_CONNECTION.PROACTIVE_RECONNECT_INTERVAL)
    expect(first.closed).toBe(true)
    await advance(SSE_CONNECTION.INITIAL_DELAY)
    expect(config.createConnection).toHaveBeenCalledTimes(2)
  })
})
