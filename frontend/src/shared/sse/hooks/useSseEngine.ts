import { useEffect, useRef, useCallback } from 'react'

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
  const eventSourceRef = useRef<EventSource | null>(null)
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const proactiveReconnectRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const idleResetTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const connectionStartTimeRef = useRef<number>(0)
  // Holds the latest `connect` so the idle-reset timer can re-arm the connection
  // without depending on `connect` (which would recreate the reconnect callbacks).
  const connectRef = useRef<() => void>(() => {})

  // SSE store actions - use selectors for actions only, not state
  // Reading reconnectAttempts via selector would cause connect() to be recreated
  // on every increment, triggering useEffect and bypassing exponential backoff
  const setConnected = useSseStore((s) => s.setConnected)
  const incrementReconnectAttempts = useSseStore((s) => s.incrementReconnectAttempts)
  const resetReconnectAttempts = useSseStore((s) => s.resetReconnectAttempts)

  /**
   * Clear all timers
   */
  const clearAllTimers = useCallback(() => {
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current)
      reconnectTimeoutRef.current = null
    }
    if (proactiveReconnectRef.current) {
      clearTimeout(proactiveReconnectRef.current)
      proactiveReconnectRef.current = null
    }
    if (idleResetTimeoutRef.current) {
      clearTimeout(idleResetTimeoutRef.current)
      idleResetTimeoutRef.current = null
    }
  }, [])

  /**
   * Disconnect SSE and cleanup
   */
  const disconnect = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
      eventSourceRef.current = null
    }
    clearAllTimers()
    setConnected(false)
  }, [setConnected, clearAllTimers])

  /**
   * Check if should reconnect (not already connected)
   * Called BEFORE attempting reconnection to prevent race conditions.
   * Auth state is checked by the effect's shouldConnect - if it flips false,
   * the effect will call disconnect() which clears eventSourceRef.
   */
  const shouldReconnect = useCallback((): boolean => {
    return eventSourceRef.current === null
  }, [])

  /**
   * Schedule reconnection with exponential backoff
   */
  const scheduleReconnect = useCallback(
    () => {
      const attemptsBeforeIncrement = useSseStore.getState().reconnectAttempts
      incrementReconnectAttempts()

      const delay = Math.min(
        SSE_CONNECTION.BASE_DELAY * Math.pow(2, attemptsBeforeIncrement),
        SSE_CONNECTION.MAX_DELAY
      )

      // Schedule idle reset - if no successful connection in 5 minutes, reset attempts
      if (idleResetTimeoutRef.current) {
        clearTimeout(idleResetTimeoutRef.current)
      }
      idleResetTimeoutRef.current = setTimeout(() => {
        resetReconnectAttempts()
        // Idle reset alone only clears the counter; without re-arming connect, the
        // stream stays permanently dead after MAX_ATTEMPTS. Re-attempt if still down.
        if (eventSourceRef.current === null) {
          connectRef.current()
        }
      }, SSE_CONNECTION.IDLE_RESET_TIMEOUT)

      const doReconnect = () => {
        // Check auth state BEFORE reconnecting (race condition fix)
        if (!shouldReconnect()) {
          return
        }

        const es = createConnection()
        eventSourceRef.current = es
        setupEventSource(es)
      }

      // Backend auto-refresh handles token expiry transparently
      reconnectTimeoutRef.current = setTimeout(doReconnect, delay)
    },
    [incrementReconnectAttempts, resetReconnectAttempts, shouldReconnect, createConnection]
  )

  /**
   * Setup EventSource event handlers
   */
  const setupEventSource = useCallback(
    (es: EventSource) => {
      es.onopen = () => {
        connectionStartTimeRef.current = Date.now()
        setConnected(true)
        resetReconnectAttempts()

        // Clear idle reset timer on successful connection
        if (idleResetTimeoutRef.current) {
          clearTimeout(idleResetTimeoutRef.current)
          idleResetTimeoutRef.current = null
        }

        // Schedule proactive reconnect BEFORE token expires
        // Backend auto-refresh will handle token expiry on reconnection
        if (proactiveReconnectRef.current) {
          clearTimeout(proactiveReconnectRef.current)
        }
        proactiveReconnectRef.current = setTimeout(() => {
          // Proactive reconnect - close and reconnect with fresh token
          if (eventSourceRef.current) {
            eventSourceRef.current.close()
            eventSourceRef.current = null
          }
          setConnected(false)

          reconnectTimeoutRef.current = setTimeout(() => {
            resetReconnectAttempts()
            if (shouldReconnect()) {
              const newEs = createConnection()
              eventSourceRef.current = newEs
              setupEventSource(newEs)
            }
          }, SSE_CONNECTION.INITIAL_DELAY)
        }, SSE_CONNECTION.PROACTIVE_RECONNECT_INTERVAL)
      }

      es.addEventListener(SSE_EVENTS.CONNECTED, () => {
        setConnected(true)
      })

      // Domain event listeners injected by the caller
      Object.entries(handlers).forEach(([type, handler]) => {
        es.addEventListener(type, handler)
      })

      es.onerror = () => {
        es.close()
        eventSourceRef.current = null
        setConnected(false)

        // Clear proactive timer since connection failed
        if (proactiveReconnectRef.current) {
          clearTimeout(proactiveReconnectRef.current)
          proactiveReconnectRef.current = null
        }

        // Check if max attempts reached
        const currentAttempts = useSseStore.getState().reconnectAttempts
        if (currentAttempts >= SSE_CONNECTION.MAX_ATTEMPTS) {
          console.warn('SSE: Max reconnection attempts reached, waiting for idle reset')
          return
        }

        scheduleReconnect()
      }
    },
    [handlers, setConnected, resetReconnectAttempts, shouldReconnect, scheduleReconnect, createConnection]
  )

  /**
   * Connect to SSE endpoint
   */
  const connect = useCallback(() => {
    // Guard: already connected
    if (eventSourceRef.current) return

    // Read reconnectAttempts directly from store to avoid dependency loop
    const currentAttempts = useSseStore.getState().reconnectAttempts

    // Guard: max attempts reached
    if (currentAttempts >= SSE_CONNECTION.MAX_ATTEMPTS) {
      console.warn('SSE: Max reconnection attempts reached, giving up')
      return
    }

    const es = createConnection()
    eventSourceRef.current = es
    setupEventSource(es)
  }, [setupEventSource, createConnection])

  // Keep the ref pointing at the latest connect for the idle-reset recovery path.
  connectRef.current = connect

  /**
   * Manage connection based on the injected shouldConnect gate
   */
  useEffect(() => {
    if (shouldConnect) {
      // Delay initial connection to let auth cookies settle after login
      // This prevents 403 errors from race conditions
      const connectTimeout = setTimeout(connect, SSE_CONNECTION.INITIAL_DELAY)
      return () => {
        clearTimeout(connectTimeout)
        disconnect()
      }
    } else {
      disconnect()
      resetReconnectAttempts()
      return () => {
        disconnect()
      }
    }
  }, [shouldConnect, connect, disconnect, resetReconnectAttempts])
}
