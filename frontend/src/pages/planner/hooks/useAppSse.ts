import { useQueryClient } from '@tanstack/react-query'

import i18n from '@/lib/i18n'
import { SSE_EVENTS } from '@/lib/constants'
import { formatUsername } from '@/lib/formatUsername'
import {
  useSseEngine,
  useSseStore,
  SseEnvelopeSchema,
  SseAccountSuspendedSchema,
  type SseEnvelope,
} from '@/shared/sse'
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
import { plannerQueryKeys } from '../lib/plannerQueryKeys'
import { SsePlannerPayloadSchema } from '../schemas/PlannerSchemas'
import { usePlannerStorage } from './usePlannerStorage'
import { userPlannersQueryKeys } from './useMDUserPlannersData'

import type { SseNotificationEvent, NotificationType } from '@/shared/notifications'

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
  const storage = usePlannerStorage()
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
   * On 'deleted', purge the row from IndexedDB so a stale local copy can't
   * trigger an upsert against the soft-deleted server row on next edit.
   * The underlying IndexedDB delete is idempotent — safe if the row is
   * already absent (e.g. self-originated event echoed back).
   */
  const applyPlannerDeleted = (_envelope: SseEnvelope, deletedId: string | undefined) => {
    if (!deletedId) return

    void storage.deleteFromLocal(deletedId).then((purge) => {
      if (!purge.ok) {
        console.error('Failed to purge local planner after SSE delete:', purge.error.kind)
      }
    })
    queryClient.setQueryData(plannerQueryKeys.list(), (prev) =>
      Array.isArray(prev) ? prev.filter((p) => p?.id !== deletedId) : prev,
    )
  }

  const applyPlannerUpsert = (envelope: SseEnvelope, plannerId: string | undefined) => {
    if (!plannerId) return

    const parsed = SsePlannerPayloadSchema.safeParse(envelope.payload)
    if (parsed.success) {
      queryClient.setQueryData(plannerQueryKeys.detail(plannerId), parsed.data)
      queryClient.setQueryData(plannerQueryKeys.list(), (prev) => upsertById(prev, parsed.data))
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

  const PLANNER_EVENT_APPLIERS: Partial<
    Record<SseEnvelope['type'], (envelope: SseEnvelope, plannerId: string | undefined) => void>
  > = {
    deleted: applyPlannerDeleted,
    created: applyPlannerUpsert,
    updated: applyPlannerUpsert,
  }

  const handlePlannerUpdate = (event: MessageEvent) => {
    let envelope: SseEnvelope
    try {
      envelope = SseEnvelopeSchema.parse(JSON.parse(event.data as string))
    } catch (e) {
      console.error('Failed to parse SSE planner-update event:', e)
      return
    }

    PLANNER_EVENT_APPLIERS[envelope.type]?.(envelope, envelope.entityId ?? envelope.plannerId)
    setLastEventTime(Date.now())
  }

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

  const createConnection = () => plannerApi.createEventsConnection()

  const handlers = {
    [SSE_EVENTS.CREATED]: handlePlannerUpdate,
    [SSE_EVENTS.UPDATED]: handlePlannerUpdate,
    [SSE_EVENTS.DELETED]: handlePlannerUpdate,
    [SSE_EVENTS.NOTIFY_COMMENT]: handleNotification,
    [SSE_EVENTS.NOTIFY_RECOMMENDED]: handleNotification,
    [SSE_EVENTS.NOTIFY_PUBLISHED]: handlePublished,
    [SSE_EVENTS.ACCOUNT_SUSPENDED]: handleAccountSuspended,
  }

  useSseEngine({ shouldConnect, createConnection, handlers })
}
