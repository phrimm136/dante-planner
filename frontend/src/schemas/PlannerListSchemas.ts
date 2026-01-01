import { z } from 'zod'
import { PLANNER_KEYWORDS } from '@/lib/constants'
import { MDCategorySchema } from './PlannerSchemas'

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
 * Vote direction schema
 */
export const VoteDirectionSchema = z.enum(['UP', 'DOWN'])

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
  /** MD category (5F, 10F, 15F) */
  category: MDCategorySchema,
  /** Selected planner keywords */
  selectedKeywords: z.array(z.string()).nullable(),
  /** Number of upvotes */
  upvotes: z.number().int().min(0),
  /** Number of downvotes */
  downvotes: z.number().int().min(0),
  /** Number of views */
  viewCount: z.number().int().min(0),
  /** Display name of the author */
  authorName: z.string(),
  /** ISO 8601 timestamp when planner was created */
  createdAt: z.string(),
  /** ISO 8601 timestamp when planner was last modified */
  lastModifiedAt: z.string().nullable(),
  /** Current user's vote (null if not voted or not authenticated) */
  userVote: VoteDirectionSchema.nullable(),
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
  /** New vote state (null if vote was removed) */
  vote: VoteDirectionSchema.nullable(),
  /** Updated upvote count */
  upvoteCount: z.number().int().min(0),
  /** Updated downvote count */
  downvoteCount: z.number().int().min(0),
}).strict()

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
