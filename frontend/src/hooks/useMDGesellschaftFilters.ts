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

import { useSearch, useNavigate } from '@tanstack/react-router'

import type { MDCategory } from '@/lib/constants'
import type { MDGesellschaftMode, MDGesellschaftSearchParams } from '@/types/MDPlannerListTypes'

// ============================================================================
// Default Values
// ============================================================================

const DEFAULT_PAGE = 0
const DEFAULT_MODE: MDGesellschaftMode = 'published'

// ============================================================================
// Return Type
// ============================================================================

export interface UseMDGesellschaftFiltersResult {
  /** MD category filter (undefined = all categories) */
  category: MDCategory | undefined
  /** Current page (0-indexed) */
  page: number
  /** Display mode: 'published' (all) or 'best' (recommended only) */
  mode: MDGesellschaftMode
  /** Search query for title filtering */
  search: string
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
  // Get current search params from URL
  // Route must define validateSearch for type safety
  const search = useSearch({ strict: false }) as MDGesellschaftSearchParams | undefined

  const navigate = useNavigate()

  // Extract values with defaults
  const category = search?.category
  const page = search?.page ?? DEFAULT_PAGE
  const mode = search?.mode ?? DEFAULT_MODE
  const searchQuery = search?.q ?? ''

  /**
   * Update filter values in URL
   * Merges with existing params, defaults are omitted to keep URL clean
   */
  const setFilters = (updates: Partial<MDGesellschaftSearchParams>) => {
    void navigate({
      to: '.',
      search: (prev) => {
        const next = { ...prev, ...updates }
        // Remove defaults to keep URL clean
        if (next.page === 0) delete next.page
        if (next.mode === 'published') delete next.mode
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
    category,
    page,
    mode,
    search: searchQuery,
    setFilters,
    clearFilters,
    resetPage,
    showBest,
    showAll,
  }
}
