import { createStore } from 'zustand'
import type { FilterState, FilterStore } from '@/components/hooks/useSetFilters'

/**
 * A filter store pinned to one state, for driving list components that subscribe to
 * their own visibility instead of taking filter props.
 *
 * @example
 * const store = createTestFilterStore<IdentityFacetState>(
 *   { ...EMPTY_FACETS, selectedAttributes: new Set(['AZURE']) },
 *   'rupture',
 * )
 * render(<IdentityList identities={items} store={store} />)
 */
export function createTestFilterStore<T>(values: T, searchQuery = ''): FilterStore<T> {
  return createStore<FilterState<T>>(() => ({ values, searchQuery }))
}
