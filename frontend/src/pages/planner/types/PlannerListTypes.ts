import type { z } from 'zod'
import type { MDCategory, RRCategory, PlannerType } from '@/shared/gameData'
import type {
  PublicPlannerSchema,
  PaginatedPlannersSchema,
  VoteResponseSchema,
  SubscriptionResponseSchema,
  PublishedPlannerDetailSchema,
} from '../schemas/PlannerListSchemas'

// Re-export category types for consumers
export type { MDCategory, RRCategory, PlannerType }

/**
 * Planner List Types
 *
 * View/sort vocabulary for the planner list page. API response shapes are
 * re-exported from their schemas (z.infer); field documentation lives on the
 * schema properties.
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
 * Sort options for planner list
 * - 'recent': Sort by lastModifiedAt descending
 * - 'popular': Sort by viewCount descending
 * - 'votes': Sort by (upvoteCount - downvoteCount) descending
 */
export type PlannerSortOption = 'recent' | 'popular' | 'votes'

// ============================================================================
// API Response Types
// ============================================================================

/** Public planner from API (matches backend PublicPlannerResponse) */
export type PublicPlanner = z.infer<typeof PublicPlannerSchema>

/** Spring Data page of public planners */
export type PaginatedPlanners = z.infer<typeof PaginatedPlannersSchema>

// ============================================================================
// Action Response Types
// ============================================================================

/** Response from vote endpoint */
export type VoteResponse = z.infer<typeof VoteResponseSchema>

/** Response from subscription toggle endpoint */
export type SubscriptionResponse = z.infer<typeof SubscriptionResponseSchema>

/**
 * Published planner detail (PublicPlanner plus content and sync metadata).
 * Carries everything needed to reconstruct a SaveablePlanner for PlannerViewer.
 */
export type PublishedPlannerDetail = z.infer<typeof PublishedPlannerDetailSchema>
