import { useState } from 'react'

export interface UseSetFiltersResult<T extends Record<string, ReadonlySet<unknown>>> {
  /** Current value of each registered filter Set */
  values: T
  /** Per-key setter, replaces that filter's Set */
  setters: { [K in keyof T]: (next: T[K]) => void }
  /** Resets every registered filter to an empty Set — derived from the registered keys, cannot go stale */
  resetAll: () => void
}

/**
 * Owns a page's Set-based filter states behind a single registration point.
 *
 * Every key of the initial record is a registered filter; `resetAll` clears
 * all of them. Adding a filter to the record automatically includes it in
 * `resetAll` — this replaces the hand-maintained `handleResetAll` blocks that
 * silently went stale when a new filter was added.
 *
 * Non-Set filter state (e.g. a search query string) stays outside this hook;
 * compose it at the call site: `() => { resetAll(); setSearchQuery('') }`.
 *
 * @param initialFilters - Record of filter keys to their initial Sets (captured on first render)
 *
 * @example
 * ```tsx
 * const { values: filters, setters, resetAll } = useSetFilters({
 *   selectedSinners: new Set<string>(),
 *   selectedSeasons: new Set<Season>(),
 * })
 *
 * <CompactSinnerFilter
 *   selectedSinners={filters.selectedSinners}
 *   onSelectionChange={setters.selectedSinners}
 * />
 * ```
 */
export function useSetFilters<T extends Record<string, ReadonlySet<unknown>>>(
  initialFilters: T,
): UseSetFiltersResult<T> {
  const [values, setValues] = useState<T>(initialFilters)

  const setters = {} as { [K in keyof T]: (next: T[K]) => void }
  for (const key of Object.keys(values) as (keyof T)[]) {
    setters[key] = (next: T[keyof T]) => {
      setValues((prev) => ({ ...prev, [key]: next }))
    }
  }

  const resetAll = () => {
    setValues((prev) => {
      const cleared = {} as T
      for (const key of Object.keys(prev) as (keyof T)[]) {
        cleared[key] = new Set() as unknown as T[keyof T]
      }
      return cleared
    })
  }

  return { values, setters, resetAll }
}
