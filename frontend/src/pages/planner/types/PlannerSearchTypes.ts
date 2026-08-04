/**
 * Planner Search Types
 *
 * Filter state for searching planners by content items.
 * Used by both published plan search (BE query) and personal plan search (FE local filtering).
 *
 * URL format: ?q={title}&keyword=Burst,Breath&identity=10212&ego=20301&gift=9001&themePack=1001
 *
 * Gift ids address the base gift, never an enhanced encoding: content stores 19154/29154 for
 * base 9154, and both the server-side filter index and the local predicate index by base.
 */

// ============================================================================
// Filter State
// ============================================================================

/**
 * Planner search filter state
 * All filters AND-composed: a plan passes only if it matches every active filter.
 * Multiple values within a category: AND (plan must contain ALL specified items).
 */
export interface PlannerSearchFilters {
  /** Title substring search (case-insensitive) */
  title: string | null
  /** Internal keyword names, e.g. ["Burst", "Breath"] */
  keywords: string[]
  /** Identity IDs, e.g. ["10212"] */
  identityIds: string[]
  /** EGO IDs, e.g. ["20301"] */
  egoIds: string[]
  /** Base EGO Gift IDs, e.g. ["9001"] */
  giftIds: string[]
  /** Theme Pack IDs, e.g. ["1001"] */
  themePackIds: string[]
}

// ============================================================================
// URL Search Params
// ============================================================================

/**
 * URL search params for planner content search
 * Extends existing page-level search params with content filter fields.
 * Comma-separated string values in URL, parsed to arrays in hook.
 */
export interface PlannerSearchParams {
  /** Title search query */
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

// ============================================================================
// Constants
// ============================================================================

/** Empty filter state (no active filters) */
export const EMPTY_PLANNER_SEARCH_FILTERS: PlannerSearchFilters = {
  title: null,
  keywords: [],
  identityIds: [],
  egoIds: [],
  giftIds: [],
  themePackIds: [],
}
