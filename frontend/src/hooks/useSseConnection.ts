import { useEffect, useRef, useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { plannerApi } from '@/lib/plannerApi'
import i18n from '@/lib/i18n'
import { SSE_CONNECTION, SSE_EVENTS } from '@/lib/constants'
import { showBrowserNotification, isTabHidden } from '@/lib/browserNotification'
import { formatUsername } from '@/lib/formatUsername'
import { showNotificationToast } from '@/components/notifications/NotificationToast'
import { PlannerSseEventSchema } from '@/schemas/PlannerSchemas'
import { SseNotificationEventSchema, SsePublishedEventSchema } from '@/schemas/NotificationSchemas'
import { useSseStore } from '@/stores/useSseStore'
import { useAuthQueryNonBlocking } from './useAuthQuery'
import { useUserSettingsQuery } from './useUserSettings'
import { plannerQueryKeys } from './usePlannerSync'
import { userPlannersQueryKeys } from './useMDUserPlannersData'
import { notificationQueryKeys } from './useNotificationsQuery'

import type { SseNotificationEvent } from '@/schemas/NotificationSchemas'

/**
 * Show notification for SSE event.
 * - Tab hidden: browser notification (if permission granted)
 * - Tab visible: in-app toast popup (bottom-right)
 */
function showNotificationForEvent(data: SseNotificationEvent): void {
  // Build notification title based on type
  let title: string
  let type: 'COMMENT_RECEIVED' | 'REPLY_RECEIVED' | 'PLANNER_RECOMMENDED'
  switch (data.type) {
    case 'COMMENT_RECEIVED':
      title = i18n.t('notifications.types.commentReceived', { ns: 'common' })
      type = 'COMMENT_RECEIVED'
      break
    case 'REPLY_RECEIVED':
      title = i18n.t('notifications.types.replyReceived', { ns: 'common' })
      type = 'REPLY_RECEIVED'
      break
    case 'PLANNER_RECOMMENDED':
      title = i18n.t('notifications.types.plannerRecommended', { ns: 'common' })
      type = 'PLANNER_RECOMMENDED'
      break
    default:
      // REPORT_RECEIVED or unknown - don't show notification
      return
  }

  // Build notification body
  const body = data.plannerTitle
    ? data.commentSnippet
      ? `${data.plannerTitle}: ${data.commentSnippet}`
      : data.plannerTitle
    : ''

  // Build URL for navigation on click
  let url: string | undefined
  if (data.plannerId) {
    url = `/planner/md/gesellschaft/${data.plannerId}`
    if (data.commentPublicId) {
      url += `#comment-${data.commentPublicId}`
    }
  }

  // Show browser notification if tab hidden, in-app toast if visible
  if (isTabHidden()) {
    showBrowserNotification({ title, body, url })
  } else {
    showNotificationToast({ type, title, body, url })
  }
}

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
  const idleResetTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
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
   * Handle SSE notification event (comment, recommended, published)
   * Invalidates notification queries to trigger immediate UI update.
   * Shows browser notification if tab is hidden and permission granted.
   */
  const handleNotification = useCallback(
    (event: MessageEvent) => {
      // Invalidate all notification queries (inbox + unread count)
      void queryClient.invalidateQueries({ queryKey: notificationQueryKeys.all })
      setLastEventTime(Date.now())

      // Parse and show browser notification
      try {
        const parsed = SseNotificationEventSchema.safeParse(JSON.parse(event.data as string))
        if (!parsed.success) {
          console.warn('SSE notification parse failed:', parsed.error)
          return
        }

        const data = parsed.data
        showNotificationForEvent(data)
      } catch (e) {
        console.error('Failed to parse SSE notification event:', e)
      }
    },
    [queryClient, setLastEventTime]
  )

  /**
   * Handle account suspension event (ban or timeout)
   * Invalidates auth query to refresh user profile with restriction status.
   */
  const handleAccountSuspended = useCallback(
    (event: MessageEvent) => {
      setLastEventTime(Date.now())

      // Invalidate auth query to refresh user profile
      void queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })

      // Parse event for logging
      try {
        const data = JSON.parse(event.data as string)
        const suspensionType = data.suspensionType as string
        const reason = data.reason as string

        console.warn(`Account suspended (${suspensionType}):`, reason || 'No reason provided')
      } catch (e) {
        console.error('Failed to parse SSE account_suspended event:', e)
      }
    },
    [queryClient, setLastEventTime]
  )

  /**
   * Handle SSE published event (new planner published broadcast).
   * Shows notification (browser or in-app) and invalidates planner list cache.
   */
  const handlePublished = useCallback(
    (event: MessageEvent) => {
      setLastEventTime(Date.now())

      // Invalidate planner list to show new planner
      void queryClient.invalidateQueries({ queryKey: plannerQueryKeys.list() })
      // Invalidate notification queries to show new notification in inbox
      void queryClient.invalidateQueries({ queryKey: notificationQueryKeys.all })

      // Parse and show notification
      try {
        const parsed = SsePublishedEventSchema.safeParse(JSON.parse(event.data as string))
        if (!parsed.success) {
          console.warn('SSE published event parse failed:', parsed.error)
          return
        }

        const data = parsed.data
        const title = i18n.t('notifications.types.plannerPublished', { ns: 'common' })
        const authorDisplay = formatUsername(data.authorKeyword, data.authorSuffix)
        const body = `${authorDisplay}: ${data.plannerTitle}`
        const url = `/planner/md/gesellschaft/${data.plannerId}`

        // Show browser notification if tab hidden, in-app toast if visible
        if (isTabHidden()) {
          showBrowserNotification({ title, body, url })
        } else {
          showNotificationToast({ type: 'PLANNER_PUBLISHED', title, body, url })
        }
      } catch (e) {
        console.error('Failed to parse SSE published event:', e)
      }
    },
    [queryClient, setLastEventTime]
  )

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
   * Auth state is checked by the effect's shouldConnect - if user logs out,
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
      }, SSE_CONNECTION.IDLE_RESET_TIMEOUT)

      const doReconnect = () => {
        // Check auth state BEFORE reconnecting (race condition fix)
        if (!shouldReconnect()) {
          return
        }

        const es = plannerApi.createEventsConnection()
        eventSourceRef.current = es
        setupEventSource(es)
      }

      // Backend auto-refresh handles token expiry transparently
      reconnectTimeoutRef.current = setTimeout(doReconnect, delay)
    },
    [incrementReconnectAttempts, resetReconnectAttempts, shouldReconnect]
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
              const newEs = plannerApi.createEventsConnection()
              eventSourceRef.current = newEs
              setupEventSource(newEs)
            }
          }, SSE_CONNECTION.INITIAL_DELAY)
        }, SSE_CONNECTION.PROACTIVE_RECONNECT_INTERVAL)
      }

      es.addEventListener(SSE_EVENTS.CONNECTED, () => {
        setConnected(true)
      })

      es.addEventListener(SSE_EVENTS.PLANNER_UPDATE, handlePlannerUpdate)
      es.addEventListener(SSE_EVENTS.SYNC_PLANNER, handlePlannerUpdate)

      // Notification events - invalidate notification cache for immediate UI update
      es.addEventListener(SSE_EVENTS.NOTIFY_COMMENT, handleNotification)
      es.addEventListener(SSE_EVENTS.NOTIFY_RECOMMENDED, handleNotification)
      es.addEventListener(SSE_EVENTS.NOTIFY_PUBLISHED, handlePublished)

      // Account suspension event - invalidate auth to refresh user profile
      es.addEventListener('account_suspended', handleAccountSuspended)

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
    [
      handlePlannerUpdate,
      handleNotification,
      handlePublished,
      handleAccountSuspended,
      setConnected,
      resetReconnectAttempts,
      shouldReconnect,
      scheduleReconnect,
    ]
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
  }, [isAuthenticated, needsSse, connect, disconnect, resetReconnectAttempts])
}
