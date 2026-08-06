import { useEffect, useRef } from 'react'

import { SSE_CONNECTION, SSE_EVENTS } from '@/lib/constants'
import { useSseStore } from '../stores/useSseStore'

/**
 * Generic SSE connection engine — domain-free.
 *
 * Owns the connection lifecycle only: open, per-event listener wiring, error
 * backoff (exponential, capped, with idle reset), proactive pre-expiry
 * reconnect, timer cleanup, and connection-status updates. It knows
 * nothing about planners, settings, or notifications — the caller injects
 * WHEN to connect (`shouldConnect`), HOW to open a stream (`createConnection`),
 * WHAT to do per event (`handlers`), and WITH WHAT TIMINGS (`policy`). This
 * keeps `shared/sse` free of any `@/pages/*` import (sink rule); the
 * planner/settings orchestration lives in the composition-root hook that calls
 * this engine.
 */

/** Every timing the reconnect loop reads, in milliseconds. */
export interface SseReconnectPolicy {
  /** Wait before the first connection; 0 opens the stream on mount. */
  initialDelayMs: number
  /** First backoff step; doubles per attempt. */
  baseDelayMs: number
  /** Ceiling the doubling saturates at. */
  maxDelayMs: number
  /** Random extra delay ceiling added to every backoff step. */
  maxJitterMs: number
  /** Attempts before the loop gives up and waits for the idle reset. */
  maxAttempts: number
  /** Quiet period after which the attempt counter clears and connect re-arms. */
  idleResetMs: number
  /** Reconnect this long after opening to stay ahead of token expiry; null disables. */
  proactiveReconnectMs: number | null
  /** How long a connection must stay open to count as healthy. */
  stableAfterMs: number
}

/** The app-wide stream's policy, and the default for callers that omit one. */
export const DEFAULT_SSE_POLICY: SseReconnectPolicy = {
  initialDelayMs: SSE_CONNECTION.INITIAL_DELAY,
  baseDelayMs: SSE_CONNECTION.BASE_DELAY,
  maxDelayMs: SSE_CONNECTION.MAX_DELAY,
  maxJitterMs: SSE_CONNECTION.MAX_JITTER,
  maxAttempts: SSE_CONNECTION.MAX_ATTEMPTS,
  idleResetMs: SSE_CONNECTION.IDLE_RESET_TIMEOUT,
  proactiveReconnectMs: SSE_CONNECTION.PROACTIVE_RECONNECT_INTERVAL,
  stableAfterMs: SSE_CONNECTION.STABLE_CONNECTION_THRESHOLD,
}

/**
 * Where one engine keeps its connection status and attempt counter.
 *
 * Defaults to the shared `useSseStore`, which describes the app-wide stream. A
 * second concurrent stream must pass its own binding: sharing the counter would
 * let either stream's failures stretch the other's backoff and spend its
 * attempt budget.
 */
export interface SseConnectionState {
  getAttempts: () => number
  incrementAttempts: () => void
  resetAttempts: () => void
  setConnected: (connected: boolean) => void
}

export interface SseEngineConfig {
  /** Connect while true; disconnect + reset when false. */
  shouldConnect: boolean
  /**
   * Identity of the stream to open (e.g. the planner a per-planner stream
   * follows). A change closes the live connection and opens a fresh one, so a
   * caller whose `createConnection` reads changing state must supply it.
   */
  streamKey?: string
  /** Opens a fresh EventSource (e.g. `plannerApi.createEventsConnection`). */
  createConnection: () => EventSource
  /** Map of SSE event name → handler. Attached on every (re)connect. */
  handlers: Record<string, (event: MessageEvent) => void>
  /** Reconnect timings; defaults to the app-wide stream's policy. */
  policy?: SseReconnectPolicy
  /** Connection state binding; defaults to the shared SSE store. */
  state?: SseConnectionState
}

/**
 * Manages an SSE connection lifecycle driven by the injected config.
 *
 * Connection rules:
 * - Connects when `shouldConnect` is true.
 * - Disconnects when it flips false.
 * - Auto-reconnects with exponential backoff on error.
 */
export function useSseEngine({
  shouldConnect,
  streamKey = '',
  createConnection,
  handlers,
  policy = DEFAULT_SSE_POLICY,
  state,
}: SseEngineConfig): void {
  // The effect owns the whole lifecycle, so only `shouldConnect` may retrigger
  // it. Everything else reaches the running connection through this ref, which
  // the listeners read at dispatch time rather than capturing at attach time.
  const configRef = useRef({ createConnection, handlers, policy, state })
  useEffect(() => {
    configRef.current = { createConnection, handlers, policy, state }
  })

  const setConnected = useSseStore((s) => s.setConnected)
  const incrementReconnectAttempts = useSseStore((s) => s.incrementReconnectAttempts)
  const resetReconnectAttempts = useSseStore((s) => s.resetReconnectAttempts)

  useEffect(() => {
    // Timings and the state binding are read once per lifecycle; only the
    // handlers have to survive a config change mid-connection.
    const { policy: timings } = configRef.current
    const connectionState: SseConnectionState = configRef.current.state ?? {
      getAttempts: () => useSseStore.getState().reconnectAttempts,
      incrementAttempts: incrementReconnectAttempts,
      resetAttempts: resetReconnectAttempts,
      setConnected,
    }

    let eventSource: EventSource | null = null
    let reconnectTimeout: ReturnType<typeof setTimeout> | null = null
    let proactiveReconnect: ReturnType<typeof setTimeout> | null = null
    let idleResetTimeout: ReturnType<typeof setTimeout> | null = null
    // 0 until onopen fires. onerror reads this to tell a never-opened or
    // short-lived connection (keep backing off) from a healthy one (reset).
    let connectionStartTime = 0

    function clearAllTimers() {
      if (reconnectTimeout) {
        clearTimeout(reconnectTimeout)
        reconnectTimeout = null
      }
      if (proactiveReconnect) {
        clearTimeout(proactiveReconnect)
        proactiveReconnect = null
      }
      if (idleResetTimeout) {
        clearTimeout(idleResetTimeout)
        idleResetTimeout = null
      }
    }

    function disconnect() {
      if (eventSource) {
        eventSource.close()
        eventSource = null
      }
      clearAllTimers()
      connectionState.setConnected(false)
    }

    /** The one place a stream is opened. No-ops while one is already live. */
    function openStream() {
      if (eventSource) return
      const es = configRef.current.createConnection()
      eventSource = es
      attachListeners(es)
    }

    function scheduleReconnect() {
      const attemptsBeforeIncrement = connectionState.getAttempts()
      connectionState.incrementAttempts()

      const delay =
        Math.min(timings.baseDelayMs * Math.pow(2, attemptsBeforeIncrement), timings.maxDelayMs) +
        Math.random() * timings.maxJitterMs

      if (idleResetTimeout) {
        clearTimeout(idleResetTimeout)
      }
      idleResetTimeout = setTimeout(() => {
        connectionState.resetAttempts()
        // Clearing the counter alone would leave the stream permanently dead
        // after maxAttempts, so the idle reset also re-arms the connection.
        openStream()
      }, timings.idleResetMs)

      reconnectTimeout = setTimeout(openStream, delay)
    }

    function attachListeners(es: EventSource) {
      connectionStartTime = 0

      es.onopen = () => {
        connectionStartTime = Date.now()
        connectionState.setConnected(true)

        if (idleResetTimeout) {
          clearTimeout(idleResetTimeout)
          idleResetTimeout = null
        }

        // Reconnect before the token expires; the backend refreshes it on the
        // new connection.
        if (proactiveReconnect) {
          clearTimeout(proactiveReconnect)
          proactiveReconnect = null
        }
        if (timings.proactiveReconnectMs !== null) {
          proactiveReconnect = setTimeout(() => {
            if (eventSource) {
              eventSource.close()
              eventSource = null
            }
            connectionState.setConnected(false)

            reconnectTimeout = setTimeout(() => {
              connectionState.resetAttempts()
              openStream()
            }, timings.initialDelayMs)
          }, timings.proactiveReconnectMs)
        }
      }

      es.addEventListener(SSE_EVENTS.CONNECTED, () => {
        connectionState.setConnected(true)
      })

      // Domain event listeners injected by the caller. The listener resolves the
      // handler per event, so a live stream never dispatches into a stale one.
      Object.keys(configRef.current.handlers).forEach((type) => {
        es.addEventListener(type, (event) => {
          configRef.current.handlers[type]?.(event as MessageEvent)
        })
      })

      es.onerror = () => {
        es.close()
        eventSource = null
        connectionState.setConnected(false)

        // Credit the connection as healthy only if it stayed open past the
        // stability floor; a never-opened or short-lived drop keeps the attempt
        // counter climbing so backoff actually slows a flapping client.
        if (connectionStartTime > 0 && Date.now() - connectionStartTime >= timings.stableAfterMs) {
          connectionState.resetAttempts()
        }

        if (proactiveReconnect) {
          clearTimeout(proactiveReconnect)
          proactiveReconnect = null
        }

        if (connectionState.getAttempts() >= timings.maxAttempts) {
          console.warn('SSE: Max reconnection attempts reached, waiting for idle reset')
          return
        }

        scheduleReconnect()
      }
    }

    /** Opens unless the attempt budget is already spent. */
    function startInitialConnection() {
      if (eventSource) return
      if (connectionState.getAttempts() >= timings.maxAttempts) {
        console.warn('SSE: Max reconnection attempts reached, giving up')
        return
      }
      openStream()
    }

    if (!shouldConnect) {
      disconnect()
      connectionState.resetAttempts()
      return disconnect
    }

    // A non-zero initial delay lets auth cookies settle after login; opening
    // immediately races them and draws a 403.
    let connectTimeout: ReturnType<typeof setTimeout> | null = null
    if (timings.initialDelayMs > 0) {
      connectTimeout = setTimeout(startInitialConnection, timings.initialDelayMs)
    } else {
      startInitialConnection()
    }

    return () => {
      if (connectTimeout) clearTimeout(connectTimeout)
      disconnect()
    }
  }, [shouldConnect, streamKey, setConnected, incrementReconnectAttempts, resetReconnectAttempts])
}
