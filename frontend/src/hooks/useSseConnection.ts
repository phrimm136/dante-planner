import { useEffect, useRef, useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { plannerApi } from '@/lib/plannerApi'
import { PlannerSseEventSchema } from '@/schemas/PlannerSchemas'
import { useSseStore } from '@/stores/useSseStore'
import { useAuthQueryNonBlocking } from './useAuthQuery'
import { useUserSettingsQuery } from './useUserSettings'
import { plannerQueryKeys } from './usePlannerSync'
import { userPlannersQueryKeys } from './useMDUserPlannersData'

/**
 * SSE reconnection configuration
 */
const SSE_CONFIG = {
  /** Initial delay before first connection to let cookies settle */
  INITIAL_DELAY: 500,
  /** Base delay for reconnection in ms */
  BASE_DELAY: 1000,
  /** Maximum delay for reconnection in ms */
  MAX_DELAY: 8000,
  /** Maximum reconnection attempts before giving up */
  MAX_ATTEMPTS: 10,
  /** Time threshold (14 min) after which token is considered potentially stale.
   * Access token expires at 15 min, so we refresh proactively before reconnect. */
  TOKEN_STALE_THRESHOLD: 14 * 60 * 1000,
  /** Proactive reconnect interval (13 min) - reconnect BEFORE token expires to avoid errors */
  PROACTIVE_RECONNECT_INTERVAL: 13 * 60 * 1000,
} as const

/**
 * Hook that manages SSE connection lifecycle based on auth and user settings.
 *
 * Connection rules:
 * - Connects when: authenticated AND (syncEnabled OR any notification enabled)
 * - Disconnects when: not authenticated OR no features need SSE
 * - Auto-reconnects with exponential backoff on error
 *
 * On SSE events (planner-update), invalidates relevant TanStack Query caches
 * to trigger UI refresh.
 *
 * @example
 * ```tsx
 * // Use in GlobalLayout or App root
 * function GlobalLayout({ children }) {
 *   useSseConnection()
 *   return <>{children}</>
 * }
 * ```
 */
export function useSseConnection(): void {
  const queryClient = useQueryClient()
  const eventSourceRef = useRef<EventSource | null>(null)
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const proactiveReconnectRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const connectionStartTimeRef = useRef<number>(0)

  // Auth and settings state (non-blocking to avoid suspending page load)
  const { data: user } = useAuthQueryNonBlocking()
  const { data: settings, isLoading: isSettingsLoading } = useUserSettingsQuery()
  const isAuthenticated = !!user

  // Only consider settings when fully loaded
  const syncEnabled = !isSettingsLoading && settings?.syncEnabled === true
  const notificationsEnabled =
    !isSettingsLoading &&
    (settings?.notifyComments === true ||
      settings?.notifyRecommendations === true ||
      settings?.notifyNewPublications === true)

  // SSE needed for sync OR notifications
  const needsSse = syncEnabled || notificationsEnabled

  // SSE store actions - use selectors for actions only, not state
  // Reading reconnectAttempts via selector would cause connect() to be recreated
  // on every increment, triggering useEffect and bypassing exponential backoff
  const setConnected = useSseStore((s) => s.setConnected)
  const setLastEventTime = useSseStore((s) => s.setLastEventTime)
  const incrementReconnectAttempts = useSseStore((s) => s.incrementReconnectAttempts)
  const resetReconnectAttempts = useSseStore((s) => s.resetReconnectAttempts)

  /**
   * Handle SSE planner update event
   */
  const handlePlannerUpdate = useCallback(
    (event: MessageEvent) => {
      try {
        const data = PlannerSseEventSchema.parse(JSON.parse(event.data as string))

        // Invalidate relevant caches
        void queryClient.invalidateQueries({ queryKey: plannerQueryKeys.list() })
        void queryClient.invalidateQueries({
          queryKey: plannerQueryKeys.detail(data.plannerId),
        })
        void queryClient.invalidateQueries({ queryKey: userPlannersQueryKeys.all })

        setLastEventTime(Date.now())
      } catch (e) {
        console.error('Failed to parse SSE planner-update event:', e)
      }
    },
    [queryClient, setLastEventTime]
  )

  /**
   * Disconnect SSE and cleanup
   */
  const disconnect = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
      eventSourceRef.current = null
    }
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current)
      reconnectTimeoutRef.current = null
    }
    if (proactiveReconnectRef.current) {
      clearTimeout(proactiveReconnectRef.current)
      proactiveReconnectRef.current = null
    }
    setConnected(false)
  }, [setConnected])

  /**
   * Ref to hold setupEventSource to break circular dependency.
   * proactiveReconnect needs to call setupEventSource, but setupEventSource
   * schedules proactiveReconnect. Using a ref avoids stale closures.
   */
  const setupEventSourceRef = useRef<((es: EventSource) => void) | null>(null)

  /**
   * Proactively refresh token and reconnect BEFORE expiry.
   * Called by timer scheduled in onopen.
   */
  const proactiveReconnect = useCallback(() => {
    // Clear the timer ref since we're executing
    proactiveReconnectRef.current = null

    // Close current connection gracefully
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
      eventSourceRef.current = null
    }
    setConnected(false)

    // Refresh token, then reconnect
    void ApiClient.post('/api/auth/refresh')
      .catch(() => {
        // Refresh failed - user may be logged out
      })
      .finally(() => {
        // Small delay to let cookies settle, then reconnect
        reconnectTimeoutRef.current = setTimeout(() => {
          // Reset attempts since this is proactive, not error-driven
          resetReconnectAttempts()
          // Re-check if we should still connect (user may have logged out)
          if (eventSourceRef.current === null && setupEventSourceRef.current) {
            const es = plannerApi.createEventsConnection()
            eventSourceRef.current = es
            setupEventSourceRef.current(es)
          }
        }, SSE_CONFIG.INITIAL_DELAY)
      })
  }, [setConnected, resetReconnectAttempts])

  /**
   * Setup EventSource event handlers
   */
  const setupEventSource = useCallback(
    (es: EventSource) => {
      es.onopen = () => {
        connectionStartTimeRef.current = Date.now()
        setConnected(true)
        resetReconnectAttempts()

        // Schedule proactive reconnect BEFORE token expires
        if (proactiveReconnectRef.current) {
          clearTimeout(proactiveReconnectRef.current)
        }
        proactiveReconnectRef.current = setTimeout(
          proactiveReconnect,
          SSE_CONFIG.PROACTIVE_RECONNECT_INTERVAL
        )
      }

      es.addEventListener('connected', () => {
        setConnected(true)
      })

      es.addEventListener('planner-update', handlePlannerUpdate)
      es.addEventListener('sync:planner', handlePlannerUpdate)

      es.onerror = () => {
        es.close()
        eventSourceRef.current = null
        setConnected(false)

        // Clear proactive timer since connection failed
        if (proactiveReconnectRef.current) {
          clearTimeout(proactiveReconnectRef.current)
          proactiveReconnectRef.current = null
        }

        const attemptsBeforeIncrement = useSseStore.getState().reconnectAttempts
        incrementReconnectAttempts()

        const delay = Math.min(
          SSE_CONFIG.BASE_DELAY * Math.pow(2, attemptsBeforeIncrement),
          SSE_CONFIG.MAX_DELAY
        )

        const connectionAge = Date.now() - connectionStartTimeRef.current
        const tokenMayBeStale = connectionAge >= SSE_CONFIG.TOKEN_STALE_THRESHOLD

        if (tokenMayBeStale) {
          void ApiClient.post('/api/auth/refresh')
            .catch(() => {})
            .finally(() => {
              reconnectTimeoutRef.current = setTimeout(() => {
                if (eventSourceRef.current === null && setupEventSourceRef.current) {
                  const newEs = plannerApi.createEventsConnection()
                  eventSourceRef.current = newEs
                  setupEventSourceRef.current(newEs)
                }
              }, delay)
            })
        } else {
          reconnectTimeoutRef.current = setTimeout(() => {
            if (eventSourceRef.current === null && setupEventSourceRef.current) {
              const newEs = plannerApi.createEventsConnection()
              eventSourceRef.current = newEs
              setupEventSourceRef.current(newEs)
            }
          }, delay)
        }
      }
    },
    [
      handlePlannerUpdate,
      setConnected,
      resetReconnectAttempts,
      incrementReconnectAttempts,
      proactiveReconnect,
    ]
  )

  // Keep ref in sync with latest setupEventSource
  setupEventSourceRef.current = setupEventSource

  /**
   * Connect to SSE endpoint
   */
  const connect = useCallback(() => {
    // Guard: already connected
    if (eventSourceRef.current) return

    // Read reconnectAttempts directly from store to avoid dependency loop
    const currentAttempts = useSseStore.getState().reconnectAttempts

    // Guard: max attempts reached
    if (currentAttempts >= SSE_CONFIG.MAX_ATTEMPTS) {
      console.warn('SSE: Max reconnection attempts reached, giving up')
      return
    }

    const es = plannerApi.createEventsConnection()
    eventSourceRef.current = es
    setupEventSource(es)
  }, [setupEventSource])

  /**
   * Manage connection based on auth + settings (sync OR notifications)
   */
  useEffect(() => {
    const shouldConnect = isAuthenticated && needsSse

    if (shouldConnect) {
      // Delay initial connection to let auth cookies settle after login
      // This prevents 403 errors from race conditions
      const connectTimeout = setTimeout(connect, SSE_CONFIG.INITIAL_DELAY)
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
  }, [isAuthenticated, needsSse, connect, disconnect, resetReconnectAttempts])
}
