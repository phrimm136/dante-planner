import { z } from 'zod'
import { migrateKeywords } from '@/shared/gameData'
import { pagedModelSchema } from '@/lib/validation'
import { PlannerCategorySchema, PlannerTypeSchema, PlannerStatusSchema } from './PlannerSchemas'

/**
 * Planner List Schemas
 *
 * Zod schemas for runtime validation of planner list API responses.
 * These schemas mirror the TypeScript interfaces in types/PlannerListTypes.ts.
 */

// ============================================================================
// Enum Schemas
// ============================================================================

// ============================================================================
// API Response Schemas
// ============================================================================

/**
 * Public planner schema for list display
 * Matches backend PublicPlannerResponse structure
 */
export const PublicPlannerSchema = z.object({
  /** Unique identifier (UUID) */
  id: z.string().uuid(),
  /** Planner title */
  title: z.string(),
  /** Type of planner (MIRROR_DUNGEON, REFRACTED_RAILWAY) */
  plannerType: PlannerTypeSchema,
  /** Category for the planner's type (MD: 5F/10F/15F, RR: placeholder) */
  category: PlannerCategorySchema,
  /** Selected planner keywords - legacy ids remapped, unknown preserved (null preserved) */
  selectedKeywords: z.preprocess(
    (v) => (v == null ? v : migrateKeywords(v)),
    z.array(z.string()).nullish(),
  ),
  /** Number of upvotes */
  upvotes: z.number().int().min(0),
  /** Number of views */
  viewCount: z.number().int().min(0),
  /** Author's association keyword; absent once the account is gone */
  authorUsernameEpithet: z.string().nullish(),
  /** Author's random alphanumeric suffix; absent once the account is gone */
  authorUsernameSuffix: z.string().nullish(),
  /** ISO 8601 timestamp when planner was created */
  createdAt: z.string(),
  /** ISO 8601 timestamp when planner was first published */
  firstPublishedAt: z.string(),
  /** Whether current user has upvoted; false when not authenticated */
  hasUpvoted: z.boolean(),
  /** Whether current user has bookmarked; false when not authenticated */
  isBookmarked: z.boolean(),
  /** Total non-deleted comment count */
  commentCount: z.number().int().min(0),
})

/**
 * Paginated planners response schema
 * Follows the Spring Data PagedModel envelope: content plus nested page metadata
 */
export const PaginatedPlannersSchema = pagedModelSchema(PublicPlannerSchema)

// ============================================================================
// Action Response Schemas
// ============================================================================

/**
 * Vote response schema
 */
export const VoteResponseSchema = z
  .object({
    /** ID of the voted planner */
    plannerId: z.string().uuid(),
    /** Whether the user has upvoted */
    hasUpvoted: z.boolean(),
    /** Updated upvote count */
    upvoteCount: z.number().int().min(0),
  })
  .strict()

/**
 * Subscription response schema
 */
export const SubscriptionResponseSchema = z
  .object({
    /** ID of the subscribed planner */
    plannerId: z.string().uuid(),
    /** New subscription state */
    subscribed: z.boolean(),
  })
  .strict()

/**
 * Published planner detail schema (extends PublicPlannerSchema with content and metadata)
 * Used for fetching single published planner with full data
 * Includes all fields needed to reconstruct SaveablePlanner for PlannerViewer
 */
export const PublishedPlannerDetailSchema = PublicPlannerSchema.extend({
  /** ISO 8601 timestamp when the content was last modified */
  lastModifiedAt: z.string(),
  /** JSON string of planner content */
  content: z.string(),
  /** Schema version for data format migration */
  schemaVersion: z.number().int().positive(),
  /** Game content version (e.g., 6 for MD6) */
  contentVersion: z.number().int().positive(),
  /** Save status */
  status: PlannerStatusSchema,
  /** Server sync version for optimistic locking */
  syncVersion: z.number().int().positive(),
  /** Subscription status; false when not authenticated */
  isSubscribed: z.boolean(),
  /** Report status; false when not authenticated */
  hasReported: z.boolean(),
  /** Whether owner has notifications enabled (false for non-owners) */
  ownerNotificationsEnabled: z.boolean(),
  /** Total comment count for this planner */
  commentCount: z.number().int().min(0),
})
