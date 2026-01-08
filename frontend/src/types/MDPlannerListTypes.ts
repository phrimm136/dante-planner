import type { MDCategory, PlannerType } from '@/lib/constants'

// Re-export category type for consumer convenience
export type { MDCategory, PlannerType }

/**
 * MD Planner List Types
 *
 * Types for the restructured MD planner list pages:
 * - /planner/md: Personal planners (IndexedDB + Server)
 * - /planner/md/gesellschaft: Community planners (Published API)
 *
 * Key changes from PlannerListTypes.ts:
 * - REMOVED: PlannerListView (routes determine view, not tabs)
 * - REMOVED: PlannerSortOption (no sorting support)
 * - RENAMED: CommunityFilter -> MDGesellschaftMode
 * - ADDED: Route-specific search param interfaces
 */

// ============================================================================
// Filter Types
// ============================================================================

/**
 * Mode filter for Gesellschaft (community) view
 * - 'published': Show all published planners (default)
 * - 'best': Show only recommended/featured planners
 */
export type MDGesellschaftMode = 'published' | 'best'

/**
 * Vote direction for planner voting
 */
export type VoteDirection = 'UP' | 'DOWN'

// ============================================================================
// URL Search Params
// ============================================================================

/**
 * URL search params for /planner/md route (personal planners)
 * Minimal params - only category filter and pagination
 *
 * Note: Defaults (page=0, no category, empty q) are hidden from URL via
 * TanStack Router's default param omission behavior.
 */
export interface MDUserSearchParams {
  /** MD category filter (5F, 10F, 15F) */
  category?: MDCategory
  /** Current page number (0-indexed) */
  page?: number
  /** Search query for title filtering */
  q?: string
}

/**
 * URL search params for /planner/md/gesellschaft route (community planners)
 * Includes mode parameter to switch between all published and best planners
 *
 * Note: Defaults (page=0, no category, mode='published', empty q) are hidden from URL.
 */
export interface MDGesellschaftSearchParams {
  /** MD category filter (5F, 10F, 15F) */
  category?: MDCategory
  /** Current page number (0-indexed) */
  page?: number
  /** Display mode: 'published' (all) or 'best' (recommended only) */
  mode?: MDGesellschaftMode
  /** Search query for title filtering */
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
  /** Category (MD: 5F/10F/15F) */
  category: MDCategory
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
