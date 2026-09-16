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

export interface FilterStoreHandle<T extends Record<string, ReadonlySet<unknown>>> {
  /** The store itself, alive for the module's lifetime */
  store: StoreApi<FilterState<T>>
  /** Per-key setter, replaces that filter's Set */
  setters: { [K in keyof T]: (next: T[K]) => void }
  /** Replaces the free-text query */
  setSearchQuery: (next: string) => void
  /** Restores the initial record and the empty query */
  resetAll: () => void
}

export interface UseFilterStoreResult<T extends Record<string, ReadonlySet<unknown>>> {
  /** Current value of each registered filter Set */
  values: T
  /** Current free-text query */
  searchQuery: string
  /** Per-key setter, replaces that filter's Set */
  setters: { [K in keyof T]: (next: T[K]) => void }
  /** Replaces the free-text query */
  setSearchQuery: (next: string) => void
  /** Restores the initial record and the empty query */
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
 * Builds one list page's filter store and the actions over it.
 *
 * Every key of the initial record is a registered filter; `resetAll` restores all of
 * them. Called at module scope, so the state outlives any mount of the page.
 *
 * @example
 * export const identityFilterStore = createFilterStore({
 *   selectedSinners: new Set<string>(),
 *   selectedSeasons: new Set<Season>(),
 * })
 */
export function createFilterStore<T extends Record<string, ReadonlySet<unknown>>>(
  initialFilters: T,
): FilterStoreHandle<T> {
  const store = createStore<FilterState<T>>(() => ({ values: initialFilters, searchQuery: '' }))

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
    store.setState(store.getInitialState())
  }

  return { store, setters, setSearchQuery, resetAll }
}

/**
 * Subscribes a page to its filter store.
 *
 * @example
 * const { values: filters, setters, searchQuery, setSearchQuery, resetAll, store } =
 *   useFilterStore(identityFilterStore)
 */
export function useFilterStore<T extends Record<string, ReadonlySet<unknown>>>(
  handle: FilterStoreHandle<T>,
): UseFilterStoreResult<T> {
  const values = useStore(handle.store, selectValues)
  const searchQuery = useStore(handle.store, selectSearchQuery)

  return {
    values,
    searchQuery,
    setters: handle.setters,
    setSearchQuery: handle.setSearchQuery,
    resetAll: handle.resetAll,
    store: handle.store,
  }
}
