import { useCallback, useMemo } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import i18n from '@/lib/i18n'
import { SSE_EVENTS } from '@/lib/constants'
import { formatUsername } from '@/lib/formatUsername'
import { useSseEngine, useSseStore, SseEnvelopeSchema } from '@/shared/sse'
import {
  showBrowserNotification,
  isTabHidden,
  showNotificationToast,
  SseNotificationEventSchema,
  SsePublishedEventSchema,
  notificationQueryKeys,
} from '@/shared/notifications'
import { useAuthQueryNonBlocking } from '@/shared/auth'
import { useUserSettingsQuery } from '@/pages/settings'
import { plannerApi } from '../lib/plannerApi'
import { SsePlannerPayloadSchema } from '../schemas/PlannerSchemas'
import { usePlannerSaveAdapter } from './usePlannerSaveAdapter'
import { plannerQueryKeys } from './usePlannerSync'
import { userPlannersQueryKeys } from './useMDUserPlannersData'

import type { SseNotificationEvent } from '@/shared/notifications'

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
 * Replace the entry matching `item.id` in a planner list cache, or append it
 * when absent. Non-mutating; tolerates a non-array/undefined `list`.
 */
function upsertById(list: unknown, item: { id?: string } | undefined) {
  const arr = Array.isArray(list) ? list : []
  const i = arr.findIndex((p) => p?.id === item?.id)
  return i >= 0 ? arr.map((p, idx) => (idx === i ? item : p)) : [...arr, item]
}

/**
 * App-level SSE orchestration — the composition-root hook mounted once in
 * GlobalLayout.
 *
 * Owns the domain wiring the generic `useSseEngine` deliberately does not:
 * gating the connection on auth + user settings (sync OR any notification pref),
 * opening the planner event stream, and the per-event side-effects (planner
 * cache invalidation + local purge, notification cache invalidation + toast,
 * auth refresh on suspension). Lives in `pages/planner` because it composes the
 * planner and settings slices — a legal page→page dependency — which keeps the
 * SSE primitive itself free of any page import.
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
  const saveAdapter = usePlannerSaveAdapter()
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
   * Handle SSE planner update event.
   *
   * On 'deleted', purge the row from IndexedDB so a stale local copy can't
   * trigger an upsert against the soft-deleted server row on next edit.
   * The underlying IndexedDB delete is idempotent — safe if the row is
   * already absent (e.g. self-originated event echoed back).
   */
  const handlePlannerUpdate = useCallback(
    (event: MessageEvent) => {
      try {
        const data = SseEnvelopeSchema.parse(JSON.parse(event.data as string))
        const plannerId = data.entityId ?? data.plannerId

        if (data.type === 'deleted') {
          const deletedId = data.deletedId ?? plannerId
          if (deletedId) {
            void saveAdapter.deleteFromLocal(deletedId).catch((e) => {
              console.error('Failed to purge local planner after SSE delete:', e)
            })
            queryClient.setQueryData(plannerQueryKeys.list(), (prev) =>
              Array.isArray(prev) ? prev.filter((p) => p?.id !== deletedId) : prev,
            )
          }
        } else if ((data.type === 'created' || data.type === 'updated') && plannerId) {
          const parsed = SsePlannerPayloadSchema.safeParse(data.payload)
          if (parsed.success) {
            queryClient.setQueryData(plannerQueryKeys.detail(plannerId), parsed.data)
            queryClient.setQueryData(plannerQueryKeys.list(), (prev) =>
              upsertById(prev, parsed.data),
            )
          } else {
            // Contract violation: the payload must be a planner row. Never write
            // an unparsed payload into a cache — mark the row-level caches stale
            // and let consumers refetch.
            console.error('SSE planner payload failed schema parse; falling back to invalidation')
            void queryClient.invalidateQueries({ queryKey: plannerQueryKeys.detail(plannerId) })
            void queryClient.invalidateQueries({ queryKey: plannerQueryKeys.list() })
          }
          // userPlanners reads IndexedDB, so this refetch cannot race replica
          // replication; without it, mounted pages keep a pre-save syncVersion
          // and later present it to the server (409).
          void queryClient.invalidateQueries({ queryKey: userPlannersQueryKeys.all })
        }

        setLastEventTime(Date.now())
      } catch (e) {
        console.error('Failed to parse SSE planner-update event:', e)
      }
    },
    [queryClient, setLastEventTime, saveAdapter],
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

        showNotificationForEvent(parsed.data)
      } catch (e) {
        console.error('Failed to parse SSE notification event:', e)
      }
    },
    [queryClient, setLastEventTime],
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
    [queryClient, setLastEventTime],
  )

  /**
   * Handle SSE published event (new planner published broadcast).
   * Shows notification (browser or in-app) and invalidates planner list cache.
   */
  const handlePublished = useCallback(
    (event: MessageEvent) => {
      setLastEventTime(Date.now())

      let raw: unknown

      // Insert the published planner into the list cache from the payload
      try {
        raw = JSON.parse(event.data as string)
        const envelope = SseEnvelopeSchema.parse(raw)
        const published = envelope.payload as { id?: string } | undefined
        if (published?.id) {
          queryClient.setQueryData(plannerQueryKeys.list(), (prev) => upsertById(prev, published))
        }
      } catch (e) {
        console.error('Failed to parse SSE published envelope:', e)
      }
      // Invalidate notification queries to show new notification in inbox
      void queryClient.invalidateQueries({ queryKey: notificationQueryKeys.all })

      // Parse and show notification
      try {
        const parsed = SsePublishedEventSchema.safeParse(raw)
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
    [queryClient, setLastEventTime],
  )

  const createConnection = useCallback(() => plannerApi.createEventsConnection(), [])

  const handlers = useMemo(
    () => ({
      [SSE_EVENTS.PLANNER_UPDATE]: handlePlannerUpdate,
      [SSE_EVENTS.SYNC_PLANNER]: handlePlannerUpdate,
      [SSE_EVENTS.NOTIFY_COMMENT]: handleNotification,
      [SSE_EVENTS.NOTIFY_RECOMMENDED]: handleNotification,
      [SSE_EVENTS.NOTIFY_PUBLISHED]: handlePublished,
      account_suspended: handleAccountSuspended,
    }),
    [handlePlannerUpdate, handleNotification, handlePublished, handleAccountSuspended],
  )

  useSseEngine({ shouldConnect, createConnection, handlers })
}
