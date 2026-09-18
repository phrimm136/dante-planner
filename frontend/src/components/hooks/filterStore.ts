import { createStore, useStore } from 'zustand'
import type { StoreApi } from 'zustand'

export interface FilterState<T> {
  values: T
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
  store: StoreApi<FilterState<T>>
  setters: { [K in keyof T]: (next: T[K]) => void }
  setSearchQuery: (next: string) => void
  resetAll: () => void
}

export interface UseFilterStoreResult<T extends Record<string, ReadonlySet<unknown>>> {
  values: T
  searchQuery: string
  setters: { [K in keyof T]: (next: T[K]) => void }
  setSearchQuery: (next: string) => void
  resetAll: () => void
  store: FilterStore<T>
}

function selectValues<T>(state: FilterState<T>): T {
  return state.values
}

function selectSearchQuery<T>(state: FilterState<T>): string {
  return state.searchQuery
}

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
