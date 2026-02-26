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
 * Vote direction for planner voting (upvote only)
 */
export type VoteDirection = 'UP'

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
  /** Number of views */
  viewCount: number
  /** Author's association keyword (e.g., "W_CORP", "BLADE_LINEAGE") */
  authorUsernameEpithet: string
  /** Author's random alphanumeric suffix (5 characters) */
  authorUsernameSuffix: string
  /** ISO 8601 timestamp when planner was created */
  createdAt: string
  /** ISO 8601 timestamp when planner was last modified */
  lastModifiedAt: string | null
  /** Whether current user has upvoted this planner (null if not authenticated) */
  hasUpvoted: boolean | null
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
 * Response from vote endpoint
 */
export interface VoteResponse {
  /** ID of the voted planner */
  plannerId: string
  /** Whether the user has upvoted */
  hasUpvoted: boolean
  /** Updated upvote count */
  upvoteCount: number
}

/**
 * Response from subscription toggle endpoint
 */
export interface SubscriptionResponse {
  /** ID of the subscribed planner */
  plannerId: string
  /** New subscription state (true = subscribed, false = unsubscribed) */
  subscribed: boolean
}

/**
 * Response from report endpoint
 */
export interface ReportResponse {
  /** ID of the reported planner */
  plannerId: string
  /** Response message */
  message: string
}

import type { PlannerStatus } from '@/types/PlannerTypes'

/**
 * Published planner detail (extends PublicPlanner with content and metadata)
 * Used when viewing a single published planner
 * Includes all fields needed to reconstruct SaveablePlanner for PlannerViewer
 */
export interface PublishedPlannerDetail extends PublicPlanner {
  /** JSON string of planner content */
  content: string
  /** Schema version for data format migration */
  schemaVersion: number
  /** Game content version (e.g., 6 for MD6) */
  contentVersion: number
  /** Save status */
  status: PlannerStatus
  /** Server sync version for optimistic locking */
  syncVersion: number
  /** Subscription status for authenticated users (null if not authenticated) */
  isSubscribed: boolean | null
  /** Report status for authenticated users (null if not authenticated) */
  hasReported: boolean | null
  /** Whether owner has notifications enabled (false for non-owners) */
  ownerNotificationsEnabled: boolean
  /** Total comment count for this planner */
  commentCount: number
}
