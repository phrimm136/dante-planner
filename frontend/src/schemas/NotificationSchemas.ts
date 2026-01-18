import { z } from 'zod'

/**
 * Notification Schemas
 *
 * Zod schemas for runtime validation of notification API responses.
 * These schemas mirror the TypeScript interfaces in types/NotificationTypes.ts
 * and validate data from the backend notification endpoints.
 */

// ============================================================================
// Enum Schemas
// ============================================================================

/**
 * Notification type schema
 */
export const NotificationTypeSchema = z.enum([
  'PLANNER_RECOMMENDED',
  'PLANNER_PUBLISHED',
  'COMMENT_RECEIVED',
  'REPLY_RECEIVED',
  'REPORT_RECEIVED',
])

// ============================================================================
// Response Schemas
// ============================================================================

/**
 * Single notification response schema
 * Matches backend NotificationResponse DTO
 */
export const NotificationResponseSchema = z.object({
  /** Public UUID identifier */
  id: z.string().uuid(),
  /** Related content ID (planner UUID or comment ID) */
  contentId: z.string(),
  /** Type of notification */
  notificationType: NotificationTypeSchema,
  /** Whether notification has been read */
  read: z.boolean(),
  /** ISO 8601 timestamp when notification was created */
  createdAt: z.string(),
  /** ISO 8601 timestamp when notification was read (null if unread) */
  readAt: z.string().nullable(),
  // Rich content fields for display and navigation
  /** Planner UUID for navigation */
  plannerId: z.string().uuid().nullable(),
  /** Planner title for display */
  plannerTitle: z.string().nullable(),
  /** Comment content snippet for preview */
  commentSnippet: z.string().nullable(),
  /** Comment public UUID for anchor link */
  commentPublicId: z.string().uuid().nullable(),
}).strict()

/**
 * Notification inbox response schema with pagination
 * Matches backend NotificationInboxResponse DTO
 */
export const NotificationInboxResponseSchema = z.object({
  /** Array of notifications */
  notifications: z.array(NotificationResponseSchema),
  /** Current page number (0-indexed) */
  page: z.number().int().nonnegative(),
  /** Page size */
  size: z.number().int().positive(),
  /** Total notification count (including read/unread) */
  totalElements: z.number().int().nonnegative(),
  /** Total number of pages */
  totalPages: z.number().int().nonnegative(),
}).strict()

/**
 * Unread notification count response schema
 * Matches backend UnreadCountResponse DTO
 */
export const UnreadCountResponseSchema = z.object({
  /** Count of unread notifications */
  unreadCount: z.number().int().nonnegative(),
}).strict()

// ============================================================================
// SSE Event Schemas
// ============================================================================

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
  // Rich content fields (optional for PLANNER_RECOMMENDED)
  plannerId: z.string().uuid().optional(),
  plannerTitle: z.string().optional(),
  commentSnippet: z.string().optional(),
  commentPublicId: z.string().uuid().optional(),
})

/**
 * Inferred type for SSE notification event
 */
export type SseNotificationEvent = z.infer<typeof SseNotificationEventSchema>

/**
 * SSE published event payload schema.
 * Broadcast to all users when a new planner is first published.
 * Different from SseNotificationEventSchema (no id/type/contentId/createdAt).
 */
export const SsePublishedEventSchema = z.object({
  plannerId: z.string().uuid(),
  plannerTitle: z.string(),
  authorKeyword: z.string(),
  authorSuffix: z.string(),
})

/**
 * Inferred type for SSE published event
 */
export type SsePublishedEvent = z.infer<typeof SsePublishedEventSchema>
