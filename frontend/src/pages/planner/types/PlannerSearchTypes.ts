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

export interface PlannerSearchFilters {
  title: string | null
  keywords: string[]
  identityIds: string[]
  egoIds: string[]
  giftIds: string[]
  themePackIds: string[]
}

export interface PlannerSearchParams {
  q?: string
  keyword?: string
  identity?: string
  ego?: string
  gift?: string
  themePack?: string
}

export const EMPTY_PLANNER_SEARCH_FILTERS: PlannerSearchFilters = {
  title: null,
  keywords: [],
  identityIds: [],
  egoIds: [],
  giftIds: [],
  themePackIds: [],
}
