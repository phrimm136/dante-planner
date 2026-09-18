import { z } from 'zod'
import { migrateKeywords } from '@/shared/gameData'
import { pagedModelSchema } from '@/lib/validation'
import { PlannerCategorySchema, PlannerTypeSchema, PlannerStatusSchema } from './PlannerSchemas'

export const PublicPlannerSchema = z.object({
  id: z.string().uuid(),
  title: z.string(),
  plannerType: PlannerTypeSchema,
  category: PlannerCategorySchema,
  selectedKeywords: z.preprocess(
    (v) => (v == null ? v : migrateKeywords(v)),
    z.array(z.string()).nullish(),
  ),
  upvotes: z.number().int().min(0),
  viewCount: z.number().int().min(0),
  authorUsernameEpithet: z.string().nullish(),
  authorUsernameSuffix: z.string().nullish(),
  createdAt: z.string(),
  firstPublishedAt: z.string(),
  hasUpvoted: z.boolean(),
  isBookmarked: z.boolean(),
  commentCount: z.number().int().min(0),
})

/**
 * Paginated planners response schema
 * Follows the Spring Data PagedModel envelope: content plus nested page metadata
 */
export const PaginatedPlannersSchema = pagedModelSchema(PublicPlannerSchema)

export const VoteResponseSchema = z
  .object({
    plannerId: z.string().uuid(),
    hasUpvoted: z.boolean(),
    upvoteCount: z.number().int().min(0),
  })
  .strict()

export const SubscriptionResponseSchema = z
  .object({
    plannerId: z.string().uuid(),
    subscribed: z.boolean(),
  })
  .strict()

export const PublishedPlannerDetailSchema = PublicPlannerSchema.extend({
  lastModifiedAt: z.string(),
  content: z.string(),
  schemaVersion: z.number().int().positive(),
  contentVersion: z.number().int().positive(),
  status: PlannerStatusSchema,
  syncVersion: z.number().int().positive(),
  isSubscribed: z.boolean(),
  hasReported: z.boolean(),
  ownerNotificationsEnabled: z.boolean(),
  commentCount: z.number().int().min(0),
})
