import { useEffect, useRef } from 'react'

import { SSE_CONNECTION, SSE_EVENTS, SSE_TRANSPORT, type SseEventType } from '@/lib/constants'
import { useSseStore } from '../stores/useSseStore'
import { runSseStream, type SseFrame } from '../lib/sseStream'

/**
 * Generic SSE connection engine — domain-free.
 *
 * Owns the connection lifecycle only: open, per-event dispatch, error backoff
 * (exponential, capped, with idle reset), rate-limit cooldown, proactive
 * pre-expiry reconnect, timer cleanup, and connection-status updates. It knows
 * nothing about planners, settings, or notifications — the caller injects WHEN
 * to connect (`shouldConnect`), WHICH stream to read (`url`), WHAT to do per
 * event (`handlers`), and WITH WHAT TIMINGS (`policy`). This keeps `shared/sse`
 * free of any `@/pages/*` import (sink rule); the planner/settings
 * orchestration lives in the composition-root hook that calls this engine.
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

const SSE_EVENT_TYPES: readonly string[] = Object.values(SSE_EVENTS)

/** Frames naming a type outside the vocabulary dispatch to nothing. */
function isKnownEventType(type: string): type is SseEventType {
  return SSE_EVENT_TYPES.includes(type)
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
   * caller whose `url` reads changing state must supply it.
   */
  streamKey?: string
  /** API path of the stream, resolved against the API base URL. */
  url: string
  /** Map of SSE event name → handler. Resolved per dispatched event. */
  handlers: Partial<Record<SseEventType, (event: MessageEvent) => void>>
  /** Runs on every open, the first one and every reconnect alike. */
  onConnected?: () => void
  /**
   * The server answered the open with `STREAM_GONE_STATUS`. Fires once; the
   * engine stops retrying, so this is the caller's only notice.
   */
  onStreamGone?: () => void
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
 * - Waits out the server's `Retry-After` on a 429 without spending an attempt.
 */
export function useSseEngine({
  shouldConnect,
  streamKey = '',
  url,
  handlers,
  onConnected,
  onStreamGone,
  policy = DEFAULT_SSE_POLICY,
  state,
}: SseEngineConfig): void {
  // The effect owns the whole lifecycle, so only `shouldConnect` may retrigger
  // it. Everything else reaches the running connection through this ref, which
  // the callbacks read at dispatch time rather than capturing at open time.
  const configRef = useRef({ url, handlers, onConnected, onStreamGone, policy, state })
  useEffect(() => {
    configRef.current = { url, handlers, onConnected, onStreamGone, policy, state }
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

    let controller: AbortController | null = null
    let reconnectTimeout: ReturnType<typeof setTimeout> | null = null
    let proactiveReconnect: ReturnType<typeof setTimeout> | null = null
    let idleResetTimeout: ReturnType<typeof setTimeout> | null = null
    // 0 until the stream opens. The close path reads this to tell a
    // never-opened or short-lived connection (keep backing off) from a healthy
    // one (reset).
    let connectionStartTime = 0
    let serverRetryMs: number | null = null
    // A stream the server reported gone is never reopened for this lifecycle.
    let streamGone = false

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
      if (controller) {
        controller.abort()
        controller = null
      }
      clearAllTimers()
      connectionState.setConnected(false)
    }

    function dispatchFrame(frame: SseFrame) {
      if (frame.retryMs !== null) serverRetryMs = frame.retryMs

      if (frame.type === SSE_EVENTS.CONNECTED) {
        connectionState.setConnected(true)
      }
      if (frame.data === null) return
      if (!isKnownEventType(frame.type)) return

      configRef.current.handlers[frame.type]?.(
        new MessageEvent(frame.type, { data: frame.data, lastEventId: frame.id ?? '' }),
      )
    }

    function handleOpen() {
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
          if (controller) {
            controller.abort()
            controller = null
          }
          connectionState.setConnected(false)

          reconnectTimeout = setTimeout(() => {
            connectionState.resetAttempts()
            openStream()
          }, timings.initialDelayMs)
        }, timings.proactiveReconnectMs)
      }

      configRef.current.onConnected?.()
    }

    function handleClosed(status: number | null) {
      connectionState.setConnected(false)

      // A stream whose subject the server says is gone will not come back, so
      // retrying only spends the attempt budget against a certain 404.
      if (status === SSE_TRANSPORT.STREAM_GONE_STATUS) {
        streamGone = true
        clearAllTimers()
        configRef.current.onStreamGone?.()
        return
      }

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

    /**
     * A refusal is the server's own pacing, not a failed attempt, so the
     * counter stands and the cooldown never undercuts the slowest backoff tier.
     */
    function handleRateLimited(retryAfterMs: number | null) {
      connectionState.setConnected(false)

      if (proactiveReconnect) {
        clearTimeout(proactiveReconnect)
        proactiveReconnect = null
      }
      if (idleResetTimeout) {
        clearTimeout(idleResetTimeout)
        idleResetTimeout = null
      }
      if (reconnectTimeout) {
        clearTimeout(reconnectTimeout)
      }

      const delay =
        Math.max(retryAfterMs ?? 0, timings.maxDelayMs) + Math.random() * timings.maxJitterMs
      reconnectTimeout = setTimeout(openStream, delay)
    }

    /** The one place a stream is opened. No-ops while one is already live. */
    function openStream() {
      if (controller || streamGone) return
      const active = new AbortController()
      controller = active
      connectionStartTime = 0

      void runSseStream(configRef.current.url, active.signal, {
        onOpen: () => {
          if (controller === active) handleOpen()
        },
        onFrame: (frame) => {
          if (controller === active) dispatchFrame(frame)
        },
        onRateLimited: (retryAfterMs) => {
          if (controller !== active) return
          controller = null
          handleRateLimited(retryAfterMs)
        },
        onClosed: (status) => {
          if (controller !== active) return
          controller = null
          handleClosed(status)
        },
      })
    }

    function scheduleReconnect() {
      const attemptsBeforeIncrement = connectionState.getAttempts()
      connectionState.incrementAttempts()

      const backoff = Math.min(
        timings.baseDelayMs * Math.pow(2, attemptsBeforeIncrement),
        timings.maxDelayMs,
      )
      const delay = Math.max(backoff, serverRetryMs ?? 0) + Math.random() * timings.maxJitterMs

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

    /** Opens unless the attempt budget is already spent. */
    function startInitialConnection() {
      if (controller) return
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
