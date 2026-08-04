import { useEffect, useRef } from 'react'

import { SSE_CONNECTION, SSE_EVENTS } from '@/lib/constants'
import { useSseStore } from '../stores/useSseStore'

/**
 * Generic SSE connection engine — domain-free.
 *
 * Owns the connection lifecycle only: open, per-event listener wiring, error
 * backoff (exponential, capped, with idle reset), proactive pre-expiry
 * reconnect, timer cleanup, and connection-status store updates. It knows
 * nothing about planners, settings, or notifications — the caller injects
 * WHEN to connect (`shouldConnect`), HOW to open a stream (`createConnection`),
 * and WHAT to do per event (`handlers`). This keeps `shared/sse` free of any
 * `@/pages/*` import (sink rule); the planner/settings orchestration lives in
 * the composition-root hook that calls this engine.
 */
export interface SseEngineConfig {
  /** Connect while true; disconnect + reset when false. */
  shouldConnect: boolean
  /** Opens a fresh EventSource (e.g. `plannerApi.createEventsConnection`). */
  createConnection: () => EventSource
  /** Map of SSE event name → handler. Attached on every (re)connect. */
  handlers: Record<string, (event: MessageEvent) => void>
}

/**
 * Manages an SSE connection lifecycle driven by the injected config.
 *
 * Connection rules:
 * - Connects when `shouldConnect` is true.
 * - Disconnects when it flips false.
 * - Auto-reconnects with exponential backoff on error.
 */
export function useSseEngine({ shouldConnect, createConnection, handlers }: SseEngineConfig): void {
  // The effect owns the whole lifecycle, so only `shouldConnect` may retrigger
  // it. Everything else reaches the running connection through this ref, which
  // the listeners read at dispatch time rather than capturing at attach time.
  const configRef = useRef({ createConnection, handlers })
  configRef.current = { createConnection, handlers }

  const setConnected = useSseStore((s) => s.setConnected)
  const incrementReconnectAttempts = useSseStore((s) => s.incrementReconnectAttempts)
  const resetReconnectAttempts = useSseStore((s) => s.resetReconnectAttempts)

  useEffect(() => {
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
      setConnected(false)
    }

    /** The one place a stream is opened. No-ops while one is already live. */
    function openStream() {
      if (eventSource) return
      const es = configRef.current.createConnection()
      eventSource = es
      attachListeners(es)
    }

    function scheduleReconnect() {
      const attemptsBeforeIncrement = useSseStore.getState().reconnectAttempts
      incrementReconnectAttempts()

      const delay =
        Math.min(
          SSE_CONNECTION.BASE_DELAY * Math.pow(2, attemptsBeforeIncrement),
          SSE_CONNECTION.MAX_DELAY,
        ) +
        Math.random() * SSE_CONNECTION.MAX_JITTER

      if (idleResetTimeout) {
        clearTimeout(idleResetTimeout)
      }
      idleResetTimeout = setTimeout(() => {
        resetReconnectAttempts()
        // Clearing the counter alone would leave the stream permanently dead
        // after MAX_ATTEMPTS, so the idle reset also re-arms the connection.
        openStream()
      }, SSE_CONNECTION.IDLE_RESET_TIMEOUT)

      reconnectTimeout = setTimeout(openStream, delay)
    }

    function attachListeners(es: EventSource) {
      connectionStartTime = 0

      es.onopen = () => {
        connectionStartTime = Date.now()
        setConnected(true)

        if (idleResetTimeout) {
          clearTimeout(idleResetTimeout)
          idleResetTimeout = null
        }

        // Reconnect before the token expires; the backend refreshes it on the
        // new connection.
        if (proactiveReconnect) {
          clearTimeout(proactiveReconnect)
        }
        proactiveReconnect = setTimeout(() => {
          if (eventSource) {
            eventSource.close()
            eventSource = null
          }
          setConnected(false)

          reconnectTimeout = setTimeout(() => {
            resetReconnectAttempts()
            openStream()
          }, SSE_CONNECTION.INITIAL_DELAY)
        }, SSE_CONNECTION.PROACTIVE_RECONNECT_INTERVAL)
      }

      es.addEventListener(SSE_EVENTS.CONNECTED, () => {
        setConnected(true)
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
        setConnected(false)

        // Credit the connection as healthy only if it stayed open past the
        // stability floor; a never-opened or short-lived drop keeps the attempt
        // counter climbing so backoff actually slows a flapping client.
        if (
          connectionStartTime > 0 &&
          Date.now() - connectionStartTime >= SSE_CONNECTION.STABLE_CONNECTION_THRESHOLD
        ) {
          resetReconnectAttempts()
        }

        if (proactiveReconnect) {
          clearTimeout(proactiveReconnect)
          proactiveReconnect = null
        }

        if (useSseStore.getState().reconnectAttempts >= SSE_CONNECTION.MAX_ATTEMPTS) {
          console.warn('SSE: Max reconnection attempts reached, waiting for idle reset')
          return
        }

        scheduleReconnect()
      }
    }

    if (!shouldConnect) {
      disconnect()
      resetReconnectAttempts()
      return disconnect
    }

    // Delay the initial connection so auth cookies settle after login; opening
    // immediately races them and draws a 403.
    const connectTimeout = setTimeout(() => {
      if (eventSource) return
      if (useSseStore.getState().reconnectAttempts >= SSE_CONNECTION.MAX_ATTEMPTS) {
        console.warn('SSE: Max reconnection attempts reached, giving up')
        return
      }
      openStream()
    }, SSE_CONNECTION.INITIAL_DELAY)

    return () => {
      clearTimeout(connectTimeout)
      disconnect()
    }
  }, [shouldConnect, setConnected, incrementReconnectAttempts, resetReconnectAttempts])
}
