import { z } from 'zod'
import { PLANNER_KEYWORDS } from '@/lib/constants'
import { MDCategorySchema, PlannerTypeSchema, PlannerStatusSchema } from './PlannerSchemas'

/**
 * Planner List Schemas
 *
 * Zod schemas for runtime validation of planner list API responses.
 * These schemas mirror the TypeScript interfaces in types/PlannerListTypes.ts.
 */

// ============================================================================
// Enum Schemas
// ============================================================================

/**
 * Vote direction schema - only UP votes allowed (downvoting removed)
 */
export const VoteDirectionSchema = z.literal('UP')

/**
 * Planner keyword schema - validates against PLANNER_KEYWORDS constant
 */
export const PlannerKeywordSchema = z.enum(PLANNER_KEYWORDS)

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
  /** MD category (5F, 10F, 15F) */
  category: MDCategorySchema,
  /** Selected planner keywords */
  selectedKeywords: z.array(z.string()).nullable(),
  /** Number of upvotes */
  upvotes: z.number().int().min(0),
  /** Number of views */
  viewCount: z.number().int().min(0),
  /** Author's association keyword */
  authorUsernameKeyword: z.string(),
  /** Author's random alphanumeric suffix */
  authorUsernameSuffix: z.string(),
  /** ISO 8601 timestamp when planner was created */
  createdAt: z.string(),
  /** ISO 8601 timestamp when planner was last modified */
  lastModifiedAt: z.string().nullable(),
  /** Whether current user has upvoted (null if not authenticated) */
  hasUpvoted: z.boolean().nullable(),
  /** Whether current user has bookmarked (null if not authenticated) */
  isBookmarked: z.boolean().nullable(),
})

/**
 * Paginated planners response schema
 * Follows Spring Data Page structure
 * Uses passthrough() to allow Spring's extra fields (empty, first, last, etc.)
 */
export const PaginatedPlannersSchema = z.object({
  /** Array of planners for current page */
  content: z.array(PublicPlannerSchema),
  /** Total number of planners matching the query */
  totalElements: z.number().int().min(0),
  /** Total number of pages */
  totalPages: z.number().int().min(0),
  /** Current page number (0-indexed) */
  number: z.number().int().min(0),
  /** Page size */
  size: z.number().int().positive(),
}).passthrough()

// ============================================================================
// Action Response Schemas
// ============================================================================

/**
 * Bookmark toggle response schema
 */
export const BookmarkResponseSchema = z.object({
  /** ID of the bookmarked planner */
  plannerId: z.string().uuid(),
  /** New bookmark state */
  bookmarked: z.boolean(),
}).strict()

/**
 * Fork response schema
 */
export const ForkResponseSchema = z.object({
  /** ID of the original planner that was forked */
  originalPlannerId: z.string().uuid(),
  /** ID of the newly created planner copy */
  newPlannerId: z.string().uuid(),
  /** Success message */
  message: z.string(),
}).strict()

/**
 * Vote response schema
 */
export const VoteResponseSchema = z.object({
  /** ID of the voted planner */
  plannerId: z.string().uuid(),
  /** Whether the user has upvoted */
  hasUpvoted: z.boolean(),
  /** Updated upvote count */
  upvoteCount: z.number().int().min(0),
}).strict()

/**
 * Subscription response schema
 */
export const SubscriptionResponseSchema = z.object({
  /** ID of the subscribed planner */
  plannerId: z.string().uuid(),
  /** New subscription state */
  subscribed: z.boolean(),
}).strict()

/**
 * Report response schema
 */
export const ReportResponseSchema = z.object({
  /** ID of the reported planner */
  plannerId: z.string().uuid(),
  /** Response message */
  message: z.string(),
}).strict()

/**
 * Published planner detail schema (extends PublicPlannerSchema with content and metadata)
 * Used for fetching single published planner with full data
 * Includes all fields needed to reconstruct SaveablePlanner for PlannerViewer
 */
export const PublishedPlannerDetailSchema = PublicPlannerSchema.extend({
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
  /** Device identifier (optional) */
  deviceId: z.string().optional(),
  /** Subscription status for authenticated users (null if not authenticated) */
  isSubscribed: z.boolean().nullable(),
  /** Report status for authenticated users (null if not authenticated) */
  hasReported: z.boolean().nullable(),
  /** Whether owner has notifications enabled (false for non-owners) */
  ownerNotificationsEnabled: z.boolean(),
  /** Total comment count for this planner */
  commentCount: z.number().int().min(0),
})

// ============================================================================
// Inferred Types (use when validated data is needed)
// ============================================================================

/** Validated public planner type */
export type PublicPlannerValidated = z.infer<typeof PublicPlannerSchema>

/** Validated paginated planners type */
export type PaginatedPlannersValidated = z.infer<typeof PaginatedPlannersSchema>

/** Validated bookmark response type */
export type BookmarkResponseValidated = z.infer<typeof BookmarkResponseSchema>

/** Validated fork response type */
export type ForkResponseValidated = z.infer<typeof ForkResponseSchema>

/** Validated vote response type */
export type VoteResponseValidated = z.infer<typeof VoteResponseSchema>

/** Validated subscription response type */
export type SubscriptionResponseValidated = z.infer<typeof SubscriptionResponseSchema>

/** Validated report response type */
export type ReportResponseValidated = z.infer<typeof ReportResponseSchema>

/** Validated published planner detail type */
export type PublishedPlannerDetailValidated = z.infer<typeof PublishedPlannerDetailSchema>
