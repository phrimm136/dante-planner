/**
 * Entity Matcher
 *
 * The predicate a filtered grid subscribes through: every facet, then the
 * search box.
 */

import type { FilterState } from '@/components/hooks/useSetFilters'
import { applyFacets, type Facet } from './applyFacets'
import { matchesSearch } from './searchTerms'

/** The predicate shape `FilteredEntityGrid` takes as its `matches` prop. */
export type EntityMatcher<TItem, TState> = (
  item: TItem,
  state: FilterState<TState>,
  searchTerms: readonly string[],
) => boolean

/**
 * Builds a list page's predicate from its facet table.
 *
 * Only for lists whose search is term-based; a list that reads raw filter state
 * or has no search box writes its own predicate.
 */
export function createEntityMatcher<TItem, TState>(
  facets: readonly Facet<TItem, TState>[],
): EntityMatcher<TItem, TState> {
  return (item, state, searchTerms) =>
    applyFacets(item, state.values, facets) && matchesSearch(state.searchQuery, searchTerms)
}
