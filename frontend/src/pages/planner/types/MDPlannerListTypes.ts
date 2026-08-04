import type { MDCategory } from '@/shared/gameData'

/**
 * MD Planner List Types
 *
 * Types for the restructured MD planner list pages:
 * - /planner/md: Personal planners (IndexedDB + Server)
 * - /planner/md/gesellschaft: Community planners (Published API)
 *
 * Route-specific search param shapes; the routes themselves determine which
 * view renders, so there is no view or sort vocabulary here.
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
  /** Comma-separated keyword names */
  keyword?: string
  /** Comma-separated identity IDs */
  identity?: string
  /** Comma-separated EGO IDs */
  ego?: string
  /** Comma-separated gift IDs */
  gift?: string
  /** Comma-separated theme pack IDs */
  themePack?: string
}
