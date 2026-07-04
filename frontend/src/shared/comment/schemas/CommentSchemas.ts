import { z } from 'zod'

/**
 * Comment System Schemas
 *
 * Zod schemas for runtime validation of comment API responses.
 * Comments are returned as a hierarchical tree from the backend.
 */

// ============================================================================
// Comment Tree Node Schema (recursive)
// ============================================================================

// Base schema without replies (to avoid circular reference)
const CommentNodeBaseSchema = z.object({
  /** Public UUID (not internal DB id) */
  id: z.string().uuid(),
  /** Comment content (empty string if deleted) */
  content: z.string(),
  /** Author association keyword (e.g., 'W_CORP') - translatable via i18n */
  authorEpithet: z.string(),
  /** Author unique suffix (e.g., 'A1B2C') */
  authorSuffix: z.string(),
  /** Whether current user is the author (computed on backend) */
  isAuthor: z.boolean(),
  /** Creation timestamp */
  createdAt: z.string(),
  /** Edit timestamp (null if never edited) */
  updatedAt: z.string().nullable(),
  /** Whether this comment was edited (shows edit indicator) */
  isUpdated: z.boolean(),
  /** Whether this comment has been deleted */
  isDeleted: z.boolean(),
  /** Number of upvotes */
  upvoteCount: z.number().int().min(0),
  /** Whether current user has upvoted */
  hasUpvoted: z.boolean(),
  /** Whether author has notifications enabled for replies */
  authorNotificationsEnabled: z.boolean(),
})

// Recursive schema with replies
export type CommentNode = z.infer<typeof CommentNodeBaseSchema> & {
  replies: CommentNode[]
}

export const CommentNodeSchema: z.ZodType<CommentNode> = CommentNodeBaseSchema.extend({
  /** Nested child comments */
  replies: z.lazy(() => z.array(CommentNodeSchema)),
})

export const CommentTreeSchema = z.array(CommentNodeSchema)

// ============================================================================
// Action Response Schemas
// ============================================================================

export const CommentVoteResponseSchema = z
  .object({
    commentId: z.string().uuid(),
    upvoteCount: z.number().int().min(0),
    hasUpvoted: z.boolean(),
  })
  .strict()

export const CommentReportResponseSchema = z
  .object({
    id: z.number().int().positive(),
    commentId: z.string().uuid(),
    reason: z.string(),
    createdAt: z.string(),
  })
  .strict()

export const CommentReportReasonSchema = z.enum(['SPAM', 'HARASSMENT', 'OFF_TOPIC', 'OTHER'])

// ============================================================================
// Inferred Types
// ============================================================================

export type CommentVoteResponse = z.infer<typeof CommentVoteResponseSchema>
export type CommentReportResponse = z.infer<typeof CommentReportResponseSchema>
export type CommentReportReason = z.infer<typeof CommentReportReasonSchema>
