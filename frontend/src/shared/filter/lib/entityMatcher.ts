import type { FilterState } from '@/components/hooks/filterStore'
import { applyFacets, type Facet } from './applyFacets'
import { matchesSearch } from './searchTerms'

export type EntityMatcher<TItem, TState> = (
  item: TItem,
  state: FilterState<TState>,
  searchTerms: readonly string[],
) => boolean

export function createEntityMatcher<TItem, TState>(
  facets: readonly Facet<TItem, TState>[],
): EntityMatcher<TItem, TState> {
  return (item, state, searchTerms) =>
    applyFacets(item, state.values, facets) && matchesSearch(state.searchQuery, searchTerms)
}
