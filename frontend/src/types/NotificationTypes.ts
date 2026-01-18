/**
 * Notification type string literals matching backend NotificationType
 */
export type NotificationType =
  | 'PLANNER_RECOMMENDED'
  | 'PLANNER_PUBLISHED'
  | 'COMMENT_RECEIVED'
  | 'REPLY_RECEIVED'
  | 'REPORT_RECEIVED'

/**
 * Single notification from server
 * Matches backend NotificationResponse DTO
 */
export interface NotificationResponse {
  /** Public UUID identifier */
  id: string
  /** Related content ID (planner UUID or comment internal ID as string) */
  contentId: string
  /** Type of notification */
  notificationType: NotificationType
  /** Whether notification has been read */
  read: boolean
  /** ISO 8601 timestamp when notification was created */
  createdAt: string
  /** ISO 8601 timestamp when notification was read (null if unread) */
  readAt: string | null
  // Rich content fields for display and navigation
  /** Planner UUID for navigation */
  plannerId: string | null
  /** Planner title for display */
  plannerTitle: string | null
  /** Comment content snippet for preview */
  commentSnippet: string | null
  /** Comment public UUID for anchor link */
  commentPublicId: string | null
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
