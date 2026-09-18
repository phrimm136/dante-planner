import { useRef } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { z } from 'zod'

import i18n from '@/lib/i18n'
import { SSE_EVENTS } from '@/lib/constants'
import { formatUsername } from '@/lib/formatUsername'
import { validateDataOrNull } from '@/lib/validation'
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

const APP_SSE_PATH = '/api/sse/subscribe'

const NOTIFICATION_TITLE_KEY: Partial<Record<NotificationType, string>> = {
  COMMENT_RECEIVED: 'notifications.types.commentReceived',
  REPLY_RECEIVED: 'notifications.types.replyReceived',
  PLANNER_RECOMMENDED: 'notifications.types.plannerRecommended',
}

/**
 * Reads an event frame's payload, degrading to `null` on a body the contract
 * does not describe. The engine calls handlers from inside the stream's read
 * loop, which a throw escapes.
 */
function readPayload<T>(event: MessageEvent, schema: z.ZodType<T>, context: string): T | null {
  let raw: unknown
  try {
    raw = JSON.parse(event.data as string)
  } catch (error) {
    console.error(`[${context}] Malformed JSON:`, error)
    return null
  }
  return validateDataOrNull(raw, schema, context)
}

function showNotificationForEvent(data: SseNotificationEvent): void {
  const titleKey = NOTIFICATION_TITLE_KEY[data.type]
  if (!titleKey) return
  const title = i18n.t(titleKey, { ns: 'common' })

  const body = data.plannerTitle
    ? data.commentSnippet
      ? `${data.plannerTitle}: ${data.commentSnippet}`
      : data.plannerTitle
    : ''

  let url: string | undefined
  if (data.plannerId) {
    url = `/planner/md/gesellschaft/${data.plannerId}`
    if (data.commentPublicId) {
      url += `#comment-${data.commentPublicId}`
    }
  }

  if (isTabHidden()) {
    showBrowserNotification({ title, body, ...(url !== undefined && { url }) })
  } else {
    showNotificationToast({ type: data.type, title, body, ...(url !== undefined && { url }) })
  }
}

export function useAppSse(): void {
  const queryClient = useQueryClient()
  const setLastEventTime = useSseStore((s) => s.setLastEventTime)

  const { data: user } = useAuthQueryNonBlocking()
  const { data: settings, isLoading: isSettingsLoading } = useUserSettingsQuery()
  const isAuthenticated = !!user

  const syncEnabled = !isSettingsLoading && settings?.syncEnabled === true
  const notificationsEnabled =
    !isSettingsLoading &&
    (settings?.notifyComments === true ||
      settings?.notifyRecommendations === true ||
      settings?.notifyNewPublications === true)

  const shouldConnect = isAuthenticated && (syncEnabled || notificationsEnabled)

  const handleNotification = (event: MessageEvent) => {
    void queryClient.invalidateQueries({ queryKey: notificationQueryKeys.all })
    setLastEventTime(Date.now())

    const data = readPayload(event, SseNotificationEventSchema, 'sse notification')
    if (!data) return

    showNotificationForEvent(data)
  }

  const handleAccountSuspended = (event: MessageEvent) => {
    setLastEventTime(Date.now())

    void queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })

    const data = readPayload(event, SseAccountSuspendedSchema, 'sse account suspended')
    if (!data) return

    const { suspensionType, reason } = data
    console.warn(`Account suspended (${suspensionType}):`, reason || 'No reason provided')
  }

  /**
   * Handle SSE published event (new planner published broadcast).
   *
   * This type is delivered as its bare payload, not as an envelope.
   */
  const handlePublished = (event: MessageEvent) => {
    setLastEventTime(Date.now())

    void queryClient.invalidateQueries({ queryKey: notificationQueryKeys.all })

    const data = readPayload(event, SsePublishedEventSchema, 'sse published')
    if (!data) return

    const title = i18n.t('notifications.types.plannerPublished', { ns: 'common' })
    const authorDisplay = formatUsername(data.authorEpithet, data.authorSuffix)
    const body = `${authorDisplay}: ${data.plannerTitle}`
    const url = `/planner/md/gesellschaft/${data.plannerId}`

    if (isTabHidden()) {
      showBrowserNotification({ title, body, url })
    } else {
      showNotificationToast({ type: 'PLANNER_PUBLISHED', title, body, url })
    }
  }

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

  useSseEngine({
    shouldConnect,
    url: APP_SSE_PATH,
    handlers,
    onConnected: handleConnected,
  })
}
