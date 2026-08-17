/**
 * Planner Search Filters Hook
 *
 * Manages planner content search filter state via URL search params.
 * Reads filter state from URL on mount, provides setter that updates both state and URL.
 *
 * URL format: ?q={title}&keyword=Burst,Breath&identity=10212&ego=20301&gift=19001&themePack=1001
 * - Comma-separated within category
 * - Empty/absent params = no filter for that category
 *
 * Pattern: useSearch + useNavigate (same as useMDGesellschaftFilters)
 */

import { useUrlFilters } from '@/components/hooks/useUrlFilters'

import type { PlannerSearchFilters, PlannerSearchParams } from '../types/PlannerSearchTypes'

// ============================================================================
// URL Param Helpers
// ============================================================================

/**
 * Parse comma-separated URL param string to array
 * Returns empty array for undefined/empty values
 */
function parseCsvParam(value: string | undefined): string[] {
  if (!value || value.trim() === '') return []
  return value
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

/**
 * Serialize array to comma-separated URL param string
 * Returns undefined for empty arrays (omitted from URL)
 */
function toCsvParam(values: string[]): string | undefined {
  return values.length > 0 ? values.join(',') : undefined
}

/**
 * Search params as an update payload, where an explicit `undefined` drops the
 * key from the URL rather than leaving the previous value in place.
 */
type PlannerSearchParamsUpdate = {
  [K in keyof PlannerSearchParams]: PlannerSearchParams[K] | undefined
}

// ============================================================================
// Return Type
// ============================================================================

export interface UsePlannerSearchFiltersResult {
  /** Current filter state parsed from URL */
  filters: PlannerSearchFilters
  /** Whether any filter is active */
  hasActiveFilters: boolean
  /** Update filter state (merges with existing, updates URL) */
  setFilters: (updates: Partial<PlannerSearchFilters>) => void
  /** Clear all search filters */
  clearFilters: () => void
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for managing planner content search filters via URL search params.
 *
 * Reads filter state from URL on mount and syncs changes back to URL.
 * Designed to compose with existing page-level filter hooks (useMDGesellschaftFilters, useMDUserFilters)
 * which manage category, page, and mode params.
 *
 * @example
 * ```tsx
 * function PlannerFilterPane() {
 *   const { filters, hasActiveFilters, setFilters, clearFilters } = usePlannerSearchFilters();
 *
 *   return (
 *     <>
 *       <Input value={filters.title ?? ''} onChange={(e) => setFilters({ title: e.target.value || null })} />
 *       <KeywordGrid selected={filters.keywords} onToggle={(kw) => {
 *         const next = filters.keywords.includes(kw)
 *           ? filters.keywords.filter((k) => k !== kw)
 *           : [...filters.keywords, kw];
 *         setFilters({ keywords: next });
 *       }} />
 *       {hasActiveFilters && <Button onClick={clearFilters}>Clear</Button>}
 *     </>
 *   );
 * }
 * ```
 */
export function usePlannerSearchFilters(): UsePlannerSearchFiltersResult {
  const { params: search, setParams } = useUrlFilters<PlannerSearchParamsUpdate>()

  // Parse URL params to filter state
  const filters: PlannerSearchFilters = {
    title: search?.q || null,
    keywords: parseCsvParam(search?.keyword),
    identityIds: parseCsvParam(search?.identity),
    egoIds: parseCsvParam(search?.ego),
    giftIds: parseCsvParam(search?.gift),
    themePackIds: parseCsvParam(search?.themePack),
  }

  const hasActiveFilters =
    filters.title !== null ||
    filters.keywords.length > 0 ||
    filters.identityIds.length > 0 ||
    filters.egoIds.length > 0 ||
    filters.giftIds.length > 0 ||
    filters.themePackIds.length > 0

  /**
   * Update filter values in URL
   * Merges with current filters, converts to URL param format.
   * Preserves existing non-search params (category, page, mode).
   */
  const setFilters = (updates: Partial<PlannerSearchFilters>) => {
    const merged = { ...filters, ...updates }

    setParams({
      q: merged.title || undefined,
      keyword: toCsvParam(merged.keywords),
      identity: toCsvParam(merged.identityIds),
      ego: toCsvParam(merged.egoIds),
      gift: toCsvParam(merged.giftIds),
      themePack: toCsvParam(merged.themePackIds),
    })
  }

  /**
   * Clear all search filters
   * Preserves existing non-search params (category, page, mode)
   */
  const clearFilters = () => {
    setParams({
      q: undefined,
      keyword: undefined,
      identity: undefined,
      ego: undefined,
      gift: undefined,
      themePack: undefined,
    })
  }

  return {
    filters,
    hasActiveFilters,
    setFilters,
    clearFilters,
  }
}
