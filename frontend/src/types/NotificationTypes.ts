/**
 * Notification type string literals matching backend NotificationType
 */
export type NotificationType =
  | 'PLANNER_RECOMMENDED'
  | 'COMMENT_RECEIVED'
  | 'REPLY_RECEIVED'
  | 'REPORT_RECEIVED'

/**
 * Single notification from server
 * Matches backend NotificationResponse DTO
 */
export interface NotificationResponse {
  /** Unique identifier */
  id: number
  /** Related content ID (planner UUID or comment ID) */
  contentId: string
  /** Type of notification */
  notificationType: NotificationType
  /** Whether notification has been read */
  read: boolean
  /** ISO 8601 timestamp when notification was created */
  createdAt: string
  /** ISO 8601 timestamp when notification was read (null if unread) */
  readAt: string | null
}

/**
 * Notification inbox response with pagination
 * Matches backend NotificationInboxResponse DTO
 */
export interface NotificationInboxResponse {
  /** Array of notifications */
  notifications: NotificationResponse[]
  /** Current page number (0-indexed) */
  page: number
  /** Page size */
  size: number
  /** Total notification count (including read/unread) */
  totalElements: number
  /** Total number of pages */
  totalPages: number
}

/**
 * Unread notification count response
 * Matches backend UnreadCountResponse DTO
 */
export interface UnreadCountResponse {
  /** Count of unread notifications */
  unreadCount: number
}
