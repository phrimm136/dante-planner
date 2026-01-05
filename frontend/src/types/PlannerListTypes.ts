import type { MDCategory, RRCategory, PlannerType } from '@/lib/constants'

// Re-export category types for consumers
export type { MDCategory, RRCategory, PlannerType }

/**
 * Planner List Types
 *
 * Types for the planner list page, including view modes, filters,
 * sort options, and API response structures.
 */

// ============================================================================
// View & Filter Types
// ============================================================================

/**
 * View mode for the planner list page
 * - 'my-plans': User's own planners (IndexedDB for guests, API for authenticated)
 * - 'community': Published planners from all users
 */
export type PlannerListView = 'my-plans' | 'community'

/**
 * Filter for community view
 * - 'all': Show all published planners
 * - 'recommended': Show only recommended/featured planners
 */
export type CommunityFilter = 'all' | 'recommended'

/**
 * Sort options for planner list
 * - 'recent': Sort by lastModifiedAt descending
 * - 'popular': Sort by viewCount descending
 * - 'votes': Sort by (upvoteCount - downvoteCount) descending
 */
export type PlannerSortOption = 'recent' | 'popular' | 'votes'

/**
 * Vote direction for planner voting
 */
export type VoteDirection = 'UP' | 'DOWN'

// ============================================================================
// URL Search Params
// ============================================================================

/**
 * URL search params type for planner list page
 * All fields optional - defaults applied in hook
 */
export interface PlannerListSearchParams {
  /** Current view mode */
  view?: PlannerListView
  /** Community filter (only applies when view is 'community') */
  filter?: CommunityFilter
  /** MD category filter */
  category?: MDCategory
  /** Current page number (0-indexed) */
  page?: number
  /** Sort option */
  sort?: PlannerSortOption
  /** Search query string */
  q?: string
}

// ============================================================================
// API Response Types
// ============================================================================

/**
 * Public planner from API (matches backend PublicPlannerResponse)
 * Contains summary data for display in list view
 */
export interface PublicPlanner {
  /** Unique identifier (UUID) */
  id: string
  /** Planner title */
  title: string
  /** Type of planner (MIRROR_DUNGEON, REFRACTED_RAILWAY) */
  plannerType: PlannerType
  /** Category (MD: 5F/10F/15F, RR: placeholder) */
  category: MDCategory | RRCategory
  /** Selected planner keywords */
  selectedKeywords: string[] | null
  /** Number of upvotes */
  upvotes: number
  /** Number of downvotes */
  downvotes: number
  /** Number of views */
  viewCount: number
  /** Display name of the author */
  authorName: string
  /** ISO 8601 timestamp when planner was created */
  createdAt: string
  /** ISO 8601 timestamp when planner was last modified */
  lastModifiedAt: string | null
  /** Current user's vote on this planner (null if not voted or not authenticated) */
  userVote: VoteDirection | null
  /** Whether current user has bookmarked this planner (null if not authenticated) */
  isBookmarked: boolean | null
}

/**
 * Paginated response for planner list
 * Follows Spring Data Page structure
 */
export interface PaginatedPlanners {
  /** Array of planners for current page */
  content: PublicPlanner[]
  /** Total number of planners matching the query */
  totalElements: number
  /** Total number of pages */
  totalPages: number
  /** Current page number (0-indexed) */
  number: number
  /** Page size */
  size: number
}

// ============================================================================
// Action Response Types
// ============================================================================

/**
 * Response from bookmark toggle endpoint
 */
export interface BookmarkResponse {
  /** ID of the bookmarked planner */
  plannerId: string
  /** New bookmark state (true = bookmarked, false = removed) */
  bookmarked: boolean
}

/**
 * Response from fork endpoint
 */
export interface ForkResponse {
  /** ID of the original planner that was forked */
  originalPlannerId: string
  /** ID of the newly created planner copy */
  newPlannerId: string
  /** Success message */
  message: string
}

/**
 * Response from vote endpoint
 */
export interface VoteResponse {
  /** ID of the voted planner */
  plannerId: string
  /** New vote state (null if vote was removed) */
  vote: VoteDirection | null
  /** Updated upvote count */
  upvoteCount: number
  /** Updated downvote count */
  downvoteCount: number
}
