/**
 * MD User Filters Hook
 *
 * Manages URL search params for /planner/md routes.
 *
 * URL behavior:
 * - page=0 is hidden (default)
 * - empty category is hidden (shows all)
 * - Filters persist in URL and are preserved during navigation
 *
 * Pattern: useSearch + useNavigate
 */

import { useUrlFilters } from '@/components/hooks/useUrlFilters'

import type { MDCategory } from '@/shared/gameData'
import type { MDUserSearchParams } from '../types/MDPlannerListTypes'

// ============================================================================
// Default Values
// ============================================================================

const DEFAULT_PAGE = 0

// ============================================================================
// Return Type
// ============================================================================

export interface UseMDUserFiltersResult {
  /** MD category filter (undefined = all categories) */
  category: MDCategory | undefined
  /** Current page (0-indexed) */
  page: number
  /** Search query for title filtering */
  search: string
  /** Update one or more filter values */
  setFilters: (updates: Partial<MDUserSearchParams>) => void
  /** Reset all filters to defaults */
  clearFilters: () => void
  /** Reset page to 0 (useful when filters change) */
  resetPage: () => void
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for managing MD user planner filter state via URL search params
 *
 * @example
 * ```tsx
 * function MDUserToolbar() {
 *   const { category, setFilters, clearFilters } = useMDUserFilters();
 *
 *   return (
 *     <>
 *       <CategorySelect
 *         value={category}
 *         onChange={(c) => setFilters({ category: c, page: 0 })}
 *       />
 *       <Button onClick={clearFilters}>Clear Filters</Button>
 *     </>
 *   );
 * }
 * ```
 */
export function useMDUserFilters(): UseMDUserFiltersResult {
  const {
    params: search,
    setParams: setFilters,
    clearParams: clearFilters,
  } = useUrlFilters<MDUserSearchParams>()

  // Extract values with defaults (handle undefined from optional schema)
  const category = search?.category
  const page = search?.page ?? DEFAULT_PAGE
  const searchQuery = search?.q ?? ''

  /**
   * Reset page to 0
   * Useful when category changes and we want to go back to first page
   */
  const resetPage = () => {
    if (page !== DEFAULT_PAGE) {
      setFilters({ page: DEFAULT_PAGE })
    }
  }

  return {
    category,
    page,
    search: searchQuery,
    setFilters,
    clearFilters,
    resetPage,
  }
}
