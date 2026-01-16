import { useEffect, useRef, useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { plannerApi } from '@/lib/plannerApi'
import { PlannerSseEventSchema } from '@/schemas/PlannerSchemas'
import { useSseStore } from '@/stores/useSseStore'
import { useAuthQuery } from './useAuthQuery'
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
  const connectionStartTimeRef = useRef<number>(0)

  // Auth and settings state
  const { data: user } = useAuthQuery()
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

  // SSE store actions
  const setConnected = useSseStore((s) => s.setConnected)
  const setLastEventTime = useSseStore((s) => s.setLastEventTime)
  const incrementReconnectAttempts = useSseStore((s) => s.incrementReconnectAttempts)
  const resetReconnectAttempts = useSseStore((s) => s.resetReconnectAttempts)
  const reconnectAttempts = useSseStore((s) => s.reconnectAttempts)

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
    setConnected(false)
  }, [setConnected])

  /**
   * Connect to SSE endpoint
   */
  const connect = useCallback(() => {
    // Guard: already connected
    if (eventSourceRef.current) return

    // Guard: max attempts reached
    if (reconnectAttempts >= SSE_CONFIG.MAX_ATTEMPTS) {
      console.warn('SSE: Max reconnection attempts reached, giving up')
      return
    }

    const es = plannerApi.createEventsConnection()
    eventSourceRef.current = es

    es.onopen = () => {
      connectionStartTimeRef.current = Date.now()
      setConnected(true)
      resetReconnectAttempts()
    }

    es.addEventListener('connected', () => {
      // Server sends 'connected' event on successful subscription
      setConnected(true)
    })

    es.addEventListener('planner-update', handlePlannerUpdate)

    // Also listen for sync:planner event type (backend sends this)
    es.addEventListener('sync:planner', handlePlannerUpdate)

    es.onerror = () => {
      es.close()
      eventSourceRef.current = null
      setConnected(false)
      incrementReconnectAttempts()

      // Exponential backoff: 1s, 2s, 4s, 8s (max)
      const delay = Math.min(
        SSE_CONFIG.BASE_DELAY * Math.pow(2, reconnectAttempts),
        SSE_CONFIG.MAX_DELAY
      )

      // Check if token may have expired (connection age > threshold)
      const connectionAge = Date.now() - connectionStartTimeRef.current
      const tokenMayBeStale = connectionAge >= SSE_CONFIG.TOKEN_STALE_THRESHOLD

      if (tokenMayBeStale) {
        // Refresh token before reconnecting - EventSource can't handle 401
        void ApiClient.post('/api/auth/refresh')
          .catch(() => {
            // Refresh failed (user may be logged out) - reconnect will fail gracefully
          })
          .finally(() => {
            reconnectTimeoutRef.current = setTimeout(connect, delay)
          })
      } else {
        reconnectTimeoutRef.current = setTimeout(connect, delay)
      }
    }
  }, [
    handlePlannerUpdate,
    reconnectAttempts,
    setConnected,
    resetReconnectAttempts,
    incrementReconnectAttempts,
  ])

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
