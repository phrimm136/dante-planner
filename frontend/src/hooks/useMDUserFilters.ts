/**
 * MD User Filters Hook
 *
 * Manages URL search params for /planner/md route (personal planners).
 * Simpler than gesellschaft - only category and page filters.
 *
 * URL behavior:
 * - page=0 is hidden (default)
 * - empty category is hidden (shows all)
 *
 * Pattern: usePlannerListFilters.ts (useSearch + useNavigate)
 */

import { useSearch, useNavigate } from '@tanstack/react-router'

import type { MDCategory } from '@/lib/constants'
import type { MDUserSearchParams } from '@/types/MDPlannerListTypes'

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
  // Get current search params from URL
  // Route must define validateSearch for type safety
  const search = useSearch({ strict: false }) as MDUserSearchParams | undefined

  const navigate = useNavigate()

  // Extract values with defaults
  const category = search?.category
  const page = search?.page ?? DEFAULT_PAGE
  const searchQuery = search?.q ?? ''

  /**
   * Update filter values in URL
   * Merges with existing params, undefined values are omitted (hidden from URL)
   */
  const setFilters = (updates: Partial<MDUserSearchParams>) => {
    void navigate({
      to: '.',
      search: (prev) => {
        const next = { ...prev, ...updates }
        // Remove defaults to keep URL clean
        if (next.page === 0) delete next.page
        if (next.category === undefined) delete next.category
        if (!next.q) delete next.q
        return next
      },
      replace: false,
    })
  }

  /**
   * Clear all filters
   * Resets to default state (empty URL params)
   */
  const clearFilters = () => {
    void navigate({
      to: '.',
      search: {},
      replace: false,
    })
  }

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
