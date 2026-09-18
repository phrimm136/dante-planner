import { z } from 'zod'

export const NotificationTypeSchema = z.enum([
  'PLANNER_RECOMMENDED',
  'PLANNER_PUBLISHED',
  'COMMENT_RECEIVED',
  'REPLY_RECEIVED',
  'REPORT_RECEIVED',
])

export const NotificationResponseSchema = z
  .object({
    id: z.string().uuid(),
    contentId: z.string(),
    notificationType: NotificationTypeSchema,
    read: z.boolean(),
    createdAt: z.string(),
    readAt: z.string().nullish(),
    plannerId: z.string().uuid().nullish(),
    plannerTitle: z.string().nullish(),
    commentSnippet: z.string().nullish(),
    commentPublicId: z.string().uuid().nullish(),
  })
  .strict()

export const NotificationInboxResponseSchema = z
  .object({
    notifications: z.array(NotificationResponseSchema),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().int().nonnegative(),
    totalPages: z.number().int().nonnegative(),
  })
  .strict()

export const NotificationBulkResultResponseSchema = z
  .object({
    affected: z.number().int().nonnegative(),
  })
  .strict()

export const UnreadCountResponseSchema = z
  .object({
    unreadCount: z.number().int().nonnegative(),
  })
  .strict()

export type NotificationType = z.infer<typeof NotificationTypeSchema>
export type NotificationResponse = z.infer<typeof NotificationResponseSchema>
export type NotificationInboxResponse = z.infer<typeof NotificationInboxResponseSchema>
export type NotificationBulkResultResponse = z.infer<typeof NotificationBulkResultResponseSchema>
export type UnreadCountResponse = z.infer<typeof UnreadCountResponseSchema>

/**
 * SSE notification event payload schema.
 * Validates data sent by backend NotificationService.pushNotification()
 * via SSE events (notify:comment, notify:recommended, notify:published).
 *
 * Note: Fields are optional because PLANNER_RECOMMENDED doesn't include
 * comment-related fields.
 */
export const SseNotificationEventSchema = z.object({
  id: z.string().uuid(),
  type: NotificationTypeSchema,
  contentId: z.string(),
  createdAt: z.string(),
  plannerId: z.string().uuid().optional(),
  plannerTitle: z.string().optional(),
  commentSnippet: z.string().optional(),
  commentPublicId: z.string().uuid().optional(),
})

export type SseNotificationEvent = z.infer<typeof SseNotificationEventSchema>

/**
 * SSE published event payload schema.
 * Broadcast to all users when a new planner is first published.
 * Different from SseNotificationEventSchema (no id/type/contentId/createdAt).
 */
export const SsePublishedEventSchema = z.object({
  plannerId: z.string().uuid(),
  plannerTitle: z.string(),
  /** Null once the author's account is gone */
  authorEpithet: z.string().nullish(),
  authorSuffix: z.string().nullish(),
})
