import { useRef } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import i18n from '@/lib/i18n'
import { SSE_EVENTS } from '@/lib/constants'
import { formatUsername } from '@/lib/formatUsername'
import { useSseEngine, useSseStore, SseAccountSuspendedSchema } from '@/shared/sse'
import {
  showBrowserNotification,
  isTabHidden,
  showNotificationToast,
  SseNotificationEventSchema,
  SsePublishedEventSchema,
  notificationQueryKeys,
} from '@/shared/notifications'
import { useAuthQueryNonBlocking } from '@/shared/auth'
import { useUserSettingsQuery, userSettingsKeys } from '@/shared/userSettings'

import type { SseNotificationEvent, NotificationType } from '@/shared/notifications'

/** The single stream carrying every app-level event type. */
const APP_SSE_PATH = '/api/sse/subscribe'

/**
 * Title i18n key per notification type. A type absent from the table raises no
 * notification at all.
 */
const NOTIFICATION_TITLE_KEY: Partial<Record<NotificationType, string>> = {
  COMMENT_RECEIVED: 'notifications.types.commentReceived',
  REPLY_RECEIVED: 'notifications.types.replyReceived',
  PLANNER_RECOMMENDED: 'notifications.types.plannerRecommended',
}

/**
 * Show notification for SSE event.
 * - Tab hidden: browser notification (if permission granted)
 * - Tab visible: in-app toast popup (bottom-right)
 */
function showNotificationForEvent(data: SseNotificationEvent): void {
  const titleKey = NOTIFICATION_TITLE_KEY[data.type]
  if (!titleKey) return
  const title = i18n.t(titleKey, { ns: 'common' })

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
    showNotificationToast({ type: data.type, title, body, url })
  }
}

/**
 * App-level SSE orchestration — the composition-root hook mounted once in
 * GlobalLayout.
 *
 * Owns the domain wiring the generic `useSseEngine` deliberately does not:
 * gating the connection on auth + user settings (sync OR any notification pref),
 * opening the app event stream, and the per-event side-effects (notification
 * cache invalidation + toast, auth refresh on suspension). Planner freshness is
 * not among them — server-backed planner queries refetch on window focus
 * instead. Lives in `pages/planner` because it composes the planner and
 * settings slices — a legal page→page dependency — which keeps the SSE
 * primitive itself free of any page import.
 *
 * @example
 * ```tsx
 * function GlobalLayout({ children }) {
 *   useAppSse()
 *   return <>{children}</>
 * }
 * ```
 */
export function useAppSse(): void {
  const queryClient = useQueryClient()
  const setLastEventTime = useSseStore((s) => s.setLastEventTime)

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
  const shouldConnect = isAuthenticated && (syncEnabled || notificationsEnabled)

  /**
   * Handle SSE notification event (comment, recommended, published)
   * Invalidates notification queries to trigger immediate UI update.
   * Shows browser notification if tab is hidden and permission granted.
   */
  const handleNotification = (event: MessageEvent) => {
    // Invalidate all notification queries (inbox + unread count)
    void queryClient.invalidateQueries({ queryKey: notificationQueryKeys.all })
    setLastEventTime(Date.now())

    let raw: unknown
    try {
      raw = JSON.parse(event.data as string)
    } catch (e) {
      console.error('Failed to parse SSE notification event:', e)
      return
    }

    const parsed = SseNotificationEventSchema.safeParse(raw)
    if (!parsed.success) {
      console.warn('SSE notification parse failed:', parsed.error)
      return
    }

    showNotificationForEvent(parsed.data)
  }

  /**
   * Handle account suspension event (ban or timeout)
   * Invalidates auth query to refresh user profile with restriction status.
   */
  const handleAccountSuspended = (event: MessageEvent) => {
    setLastEventTime(Date.now())

    // Invalidate auth query to refresh user profile
    void queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })

    let raw: unknown
    try {
      raw = JSON.parse(event.data as string)
    } catch (e) {
      console.error('Failed to parse SSE account_suspended event:', e)
      return
    }

    const parsed = SseAccountSuspendedSchema.safeParse(raw)
    if (!parsed.success) {
      console.warn('SSE account_suspended parse failed:', parsed.error)
      return
    }

    const { suspensionType, reason } = parsed.data
    console.warn(`Account suspended (${suspensionType}):`, reason || 'No reason provided')
  }

  /**
   * Handle SSE published event (new planner published broadcast).
   *
   * This type is delivered as its bare payload, not as an envelope.
   */
  const handlePublished = (event: MessageEvent) => {
    setLastEventTime(Date.now())

    // Invalidate notification queries to show new notification in inbox
    void queryClient.invalidateQueries({ queryKey: notificationQueryKeys.all })

    let raw: unknown
    try {
      raw = JSON.parse(event.data as string)
    } catch (e) {
      console.error('Failed to parse SSE published event:', e)
      return
    }

    const parsed = SsePublishedEventSchema.safeParse(raw)
    if (!parsed.success) {
      console.warn('SSE published event parse failed:', parsed.error)
      return
    }

    const data = parsed.data
    const title = i18n.t('notifications.types.plannerPublished', { ns: 'common' })
    const authorDisplay = formatUsername(data.authorEpithet, data.authorSuffix)
    const body = `${authorDisplay}: ${data.plannerTitle}`
    const url = `/planner/md/gesellschaft/${data.plannerId}`

    // Show browser notification if tab hidden, in-app toast if visible
    if (isTabHidden()) {
      showBrowserNotification({ title, body, url })
    } else {
      showNotificationToast({ type: 'PLANNER_PUBLISHED', title, body, url })
    }
  }

  /** Settings are re-read after a gap in the stream, not on the first open. */
  const hasConnectedRef = useRef(false)
  const handleConnected = () => {
    if (hasConnectedRef.current) {
      void queryClient.invalidateQueries({ queryKey: userSettingsKeys.settings() })
    }
    hasConnectedRef.current = true
  }

  const handlers = {
    [SSE_EVENTS.NOTIFY_COMMENT]: handleNotification,
    [SSE_EVENTS.NOTIFY_RECOMMENDED]: handleNotification,
    [SSE_EVENTS.NOTIFY_PUBLISHED]: handlePublished,
    [SSE_EVENTS.ACCOUNT_SUSPENDED]: handleAccountSuspended,
  }

  // This path names no resource, so its 404s are infrastructure rather than a
  // missing subject: it keeps the ordinary backoff instead of stopping.
  useSseEngine({
    shouldConnect,
    url: APP_SSE_PATH,
    handlers,
    onConnected: handleConnected,
  })
}
