import { z } from 'zod'

const CommentNodeBaseSchema = z.object({
  id: z.string().uuid(),
  parentCommentId: z.string().uuid().nullish(),
  content: z.string(),
  authorEpithet: z.string(),
  authorSuffix: z.string(),
  isAuthor: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string().nullish(),
  isDeleted: z.boolean(),
  upvoteCount: z.number().int().min(0),
  hasUpvoted: z.boolean(),
  authorNotificationsEnabled: z.boolean(),
})

export type CommentNode = z.infer<typeof CommentNodeBaseSchema> & {
  replies: CommentNode[]
}

export const CommentNodeSchema: z.ZodType<CommentNode> = CommentNodeBaseSchema.extend({
  replies: z.lazy(() => z.array(CommentNodeSchema)),
})

export const CommentTreeSchema = z.array(CommentNodeSchema)

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

export type CommentVoteResponse = z.infer<typeof CommentVoteResponseSchema>
export type CommentReportResponse = z.infer<typeof CommentReportResponseSchema>
export type CommentReportReason = z.infer<typeof CommentReportReasonSchema>
