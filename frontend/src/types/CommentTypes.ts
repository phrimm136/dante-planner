/**
 * Comment System Types
 *
 * Types for the comment system on published planners.
 * Comments are returned as a hierarchical tree from the backend.
 * No internal IDs exposed - uses UUID for comments, no user ID at all.
 */

// ============================================================================
// Comment Types
// ============================================================================

/**
 * Comment tree node from API
 * Tree structure is built server-side with replies nested
 */
export interface CommentNode {
  /** Public UUID (not internal DB id) */
  id: string
  /** Comment content (empty string if deleted) */
  content: string
  /** Author association keyword (e.g., 'W_CORP') - translatable via i18n */
  authorAssoc: string
  /** Author unique suffix (e.g., 'A1B2C') */
  authorSuffix: string
  /** Whether current user is the author (computed on backend) */
  isAuthor: boolean
  /** Creation timestamp */
  createdAt: string
  /** Edit timestamp (null if never edited) */
  updatedAt: string | null
  /** Whether this comment was edited (shows edit indicator) */
  isUpdated: boolean
  /** Whether this comment has been deleted */
  isDeleted: boolean
  /** Number of upvotes */
  upvoteCount: number
  /** Whether current user has upvoted */
  hasUpvoted: boolean
  /** Whether author has notifications enabled for replies */
  authorNotificationsEnabled: boolean
  /** Nested child comments */
  replies: CommentNode[]
}

// ============================================================================
// Action Types
// ============================================================================

/**
 * Response from comment upvote endpoint
 */
export interface CommentVoteResponse {
  commentId: string
  upvoteCount: number
  hasUpvoted: boolean
}

/**
 * Response from comment report endpoint
 */
export interface CommentReportResponse {
  id: number
  commentId: string
  reason: string
  createdAt: string
}

/**
 * Valid report reasons
 */
export type CommentReportReason = 'SPAM' | 'HARASSMENT' | 'OFF_TOPIC' | 'OTHER'
