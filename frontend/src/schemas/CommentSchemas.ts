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
  id: z.string().uuid(),
  content: z.string(),
  authorAssoc: z.string(),
  authorSuffix: z.string(),
  isAuthor: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string().nullable(),
  isUpdated: z.boolean(),
  isDeleted: z.boolean(),
  upvoteCount: z.number().int().min(0),
  hasUpvoted: z.boolean(),
  authorNotificationsEnabled: z.boolean(),
})

// Recursive schema with replies
export type CommentNode = z.infer<typeof CommentNodeBaseSchema> & {
  replies: CommentNode[]
}

export const CommentNodeSchema: z.ZodType<CommentNode> = CommentNodeBaseSchema.extend({
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

export const CommentReportReasonSchema = z.enum([
  'SPAM',
  'HARASSMENT',
  'OFF_TOPIC',
  'OTHER',
])

// ============================================================================
// Inferred Types (for backward compat - prefer CommentTypes.ts)
// ============================================================================

export type CommentNodeValidated = CommentNode
export type CommentVoteResponseValidated = z.infer<typeof CommentVoteResponseSchema>
export type CommentReportResponseValidated = z.infer<typeof CommentReportResponseSchema>
