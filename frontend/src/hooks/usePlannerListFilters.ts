/**
 * Planner List Filters Hook
 *
 * Manages URL search params for planner list page filters.
 * Enables shareable URLs and browser back/forward navigation.
 *
 * Note: Requires route definition with validateSearch to work.
 * See router.tsx for route configuration.
 */

import { useSearch, useNavigate } from '@tanstack/react-router'

import type {
  PlannerListView,
  CommunityFilter,
  MDCategory,
  PlannerSortOption,
  PlannerListSearchParams,
} from '@/types/PlannerListTypes'

// ============================================================================
// Default Values
// ============================================================================

const DEFAULT_VIEW: PlannerListView = 'community'
const DEFAULT_FILTER: CommunityFilter = 'all'
const DEFAULT_PAGE = 0
const DEFAULT_SORT: PlannerSortOption = 'recent'

// ============================================================================
// Return Type
// ============================================================================

export interface UsePlannerListFiltersResult {
  /** Current view mode */
  view: PlannerListView
  /** Community filter (all/recommended) */
  filter: CommunityFilter
  /** MD category filter */
  category: MDCategory | undefined
  /** Current page (0-indexed) */
  page: number
  /** Sort option */
  sort: PlannerSortOption
  /** Search query */
  search: string | undefined
  /** Update one or more filter values */
  setFilters: (updates: Partial<PlannerListSearchParams>) => void
  /** Reset all filters to defaults (keeps view) */
  clearFilters: () => void
  /** Reset page to 0 (useful when filters change) */
  resetPage: () => void
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for managing planner list filter state via URL search params
 *
 * @example
 * ```tsx
 * function PlannerListToolbar() {
 *   const { category, sort, setFilters, clearFilters } = usePlannerListFilters();
 *
 *   return (
 *     <>
 *       <CategorySelect
 *         value={category}
 *         onChange={(c) => setFilters({ category: c, page: 0 })}
 *       />
 *       <SortSelect
 *         value={sort}
 *         onChange={(s) => setFilters({ sort: s })}
 *       />
 *       <Button onClick={clearFilters}>Clear Filters</Button>
 *     </>
 *   );
 * }
 * ```
 */
export function usePlannerListFilters(): UsePlannerListFiltersResult {
  // Get current search params from URL
  // Route must define validateSearch for this to work
  const search = useSearch({ strict: false }) as PlannerListSearchParams | undefined

  const navigate = useNavigate()

  // Extract values with defaults
  const view = search?.view ?? DEFAULT_VIEW
  const filter = search?.filter ?? DEFAULT_FILTER
  const category = search?.category
  const page = search?.page ?? DEFAULT_PAGE
  const sort = search?.sort ?? DEFAULT_SORT
  const searchQuery = search?.q

  /**
   * Update filter values in URL
   * Merges with existing params
   */
  const setFilters = (updates: Partial<PlannerListSearchParams>) => {
    void navigate({
      search: (prev: PlannerListSearchParams) => ({
        ...prev,
        ...updates,
      }),
      replace: false,
    })
  }

  /**
   * Clear all filters except view
   * Resets to default state
   */
  const clearFilters = () => {
    void navigate({
      search: {
        view,
        filter: DEFAULT_FILTER,
        page: DEFAULT_PAGE,
        sort: DEFAULT_SORT,
      },
      replace: false,
    })
  }

  /**
   * Reset page to 0
   * Useful when filters change and we want to go back to first page
   */
  const resetPage = () => {
    if (page !== DEFAULT_PAGE) {
      setFilters({ page: DEFAULT_PAGE })
    }
  }

  return {
    view,
    filter,
    category,
    page,
    sort,
    search: searchQuery,
    setFilters,
    clearFilters,
    resetPage,
  }
}
