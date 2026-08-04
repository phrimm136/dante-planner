import { useState } from 'react'
import { createStore, useStore } from 'zustand'
import type { StoreApi } from 'zustand'

export interface FilterState<T> {
  /** Current value of each registered filter Set */
  values: T
  /** Free-text query applied alongside the facets */
  searchQuery: string
}

/**
 * Read-only handle onto a filter store. Read-only so it stays covariant in `T`: a page
 * registers mutable `Set`s, a list facet type reads `ReadonlySet`s, and both name the
 * same store.
 */
export type FilterStore<T> = Pick<
  StoreApi<FilterState<T>>,
  'getState' | 'getInitialState' | 'subscribe'
>

export interface UseSetFiltersResult<T extends Record<string, ReadonlySet<unknown>>> {
  /** Current value of each registered filter Set */
  values: T
  /** Current free-text query */
  searchQuery: string
  /** Per-key setter, replaces that filter's Set */
  setters: { [K in keyof T]: (next: T[K]) => void }
  /** Replaces the free-text query */
  setSearchQuery: (next: string) => void
  /** Resets every registered filter and the query — derived from the registered keys, cannot go stale */
  resetAll: () => void
  /** Store handle for consumers that subscribe to their own slice instead of taking props */
  store: FilterStore<T>
}

function selectValues<T>(state: FilterState<T>): T {
  return state.values
}

function selectSearchQuery<T>(state: FilterState<T>): string {
  return state.searchQuery
}

/**
 * Owns a page's filter state behind a single registration point.
 *
 * Every key of the initial record is a registered filter; `resetAll` clears all of them
 * plus the query. Adding a filter to the record automatically includes it in `resetAll`.
 *
 * State lives in a store created per mount, so a list item can subscribe to whether it
 * survives the current filters without the list re-rendering to hand it that answer.
 *
 * @param initialFilters - Record of filter keys to their initial Sets (captured on first render)
 *
 * @example
 * ```tsx
 * const { values: filters, setters, searchQuery, setSearchQuery, resetAll, store } = useSetFilters({
 *   selectedSinners: new Set<string>(),
 *   selectedSeasons: new Set<Season>(),
 * })
 *
 * <CompactSinnerFilter
 *   selected={filters.selectedSinners}
 *   onSelectionChange={setters.selectedSinners}
 * />
 * ```
 */
export function useSetFilters<T extends Record<string, ReadonlySet<unknown>>>(
  initialFilters: T,
): UseSetFiltersResult<T> {
  const [store] = useState<StoreApi<FilterState<T>>>(() =>
    createStore<FilterState<T>>(() => ({ values: initialFilters, searchQuery: '' })),
  )

  // Identity-stable for the lifetime of the hook: consumers pass these straight to a
  // filter control's onSelectionChange, where a fresh identity forces a re-render.
  const [actions] = useState(() => {
    const setters = {} as { [K in keyof T]: (next: T[K]) => void }
    for (const key of Object.keys(initialFilters) as (keyof T)[]) {
      setters[key] = (next: T[keyof T]) => {
        store.setState((prev) => ({ values: { ...prev.values, [key]: next } }))
      }
    }

    const setSearchQuery = (next: string) => {
      store.setState({ searchQuery: next })
    }

    const resetAll = () => {
      store.setState((prev) => {
        const cleared = {} as T
        for (const key of Object.keys(prev.values) as (keyof T)[]) {
          cleared[key] = new Set() as unknown as T[keyof T]
        }
        return { values: cleared, searchQuery: '' }
      })
    }

    return { setters, setSearchQuery, resetAll }
  })

  const values = useStore(store, selectValues)
  const searchQuery = useStore(store, selectSearchQuery)

  return {
    values,
    searchQuery,
    setters: actions.setters,
    setSearchQuery: actions.setSearchQuery,
    resetAll: actions.resetAll,
    store,
  }
}
