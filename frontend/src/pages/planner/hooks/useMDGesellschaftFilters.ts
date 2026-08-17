/**
 * MD Gesellschaft Filters Hook
 *
 * Manages URL search params for /planner/md/gesellschaft route (community planners).
 * Includes mode parameter to switch between all published and recommended planners.
 *
 * URL behavior:
 * - page=0 is hidden (default)
 * - mode='published' is hidden (default)
 * - empty category is hidden (shows all)
 *
 * Pattern: usePlannerListFilters.ts (useSearch + useNavigate)
 */

import { useUrlFilters } from '@/components/hooks/useUrlFilters'

import type {
  MDGesellschaftFilters,
  MDGesellschaftMode,
  MDGesellschaftSearchParams,
} from '../types/MDPlannerListTypes'

// ============================================================================
// Default Values
// ============================================================================

const DEFAULT_PAGE = 0
const DEFAULT_MODE: MDGesellschaftMode = 'published'

// ============================================================================
// Return Type
// ============================================================================

export interface UseMDGesellschaftFiltersResult {
  /** Every filter value the list needs, ready to hand on as one prop */
  filters: MDGesellschaftFilters
  /** Update one or more filter values */
  setFilters: (updates: Partial<MDGesellschaftSearchParams>) => void
  /** Reset all filters to defaults */
  clearFilters: () => void
  /** Reset page to 0 (useful when filters change) */
  resetPage: () => void
  /** Switch to recommended mode */
  showBest: () => void
  /** Switch to all published mode */
  showAll: () => void
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for managing MD gesellschaft filter state via URL search params
 *
 * @example
 * ```tsx
 * function GesellschaftToolbar() {
 *   const { category, mode, setFilters, showBest, showAll } = useMDGesellschaftFilters();
 *
 *   return (
 *     <>
 *       <CategorySelect
 *         value={category}
 *         onChange={(c) => setFilters({ category: c, page: 0 })}
 *       />
 *       <ToggleGroup value={mode}>
 *         <ToggleItem value="published" onClick={showAll}>All</ToggleItem>
 *         <ToggleItem value="best" onClick={showBest}>Best</ToggleItem>
 *       </ToggleGroup>
 *     </>
 *   );
 * }
 * ```
 */
export function useMDGesellschaftFilters(): UseMDGesellschaftFiltersResult {
  const {
    params: search,
    setParams: setFilters,
    clearParams: clearFilters,
  } = useUrlFilters<MDGesellschaftSearchParams>()

  // Extract values with defaults
  const category = search?.category
  const page = search?.page ?? DEFAULT_PAGE
  const mode = search?.mode ?? DEFAULT_MODE
  const searchQuery = search?.q ?? ''
  const keyword = search?.keyword
  const identity = search?.identity
  const ego = search?.ego
  const gift = search?.gift
  const themePack = search?.themePack

  /**
   * Reset page to 0
   * Useful when mode or category changes
   */
  const resetPage = () => {
    if (page !== DEFAULT_PAGE) {
      setFilters({ page: DEFAULT_PAGE })
    }
  }

  /**
   * Switch to recommended/best planners view
   * Resets page to 0
   */
  const showBest = () => {
    setFilters({ mode: 'best', page: 0 })
  }

  /**
   * Switch to all published planners view
   * Resets page to 0
   */
  const showAll = () => {
    setFilters({ mode: 'published', page: 0 })
  }

  return {
    filters: {
      category,
      page,
      mode,
      search: searchQuery,
      keyword,
      identity,
      ego,
      gift,
      themePack,
    },
    setFilters,
    clearFilters,
    resetPage,
    showBest,
    showAll,
  }
}
