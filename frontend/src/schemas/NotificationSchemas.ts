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
  /** Unique identifier */
  id: z.number().int().positive(),
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
