/**
 * Facet Filtering
 *
 * Set-based filter facets shared by the list pages. Search predicates are not
 * facets and stay at their call sites.
 */

export type FacetMode = 'any' | 'all'

/**
 * One filterable dimension of a list page.
 *
 * @property sel - Picks the selection Set out of the filter state. `undefined`
 *   skips the facet entirely (mode-gated facets return it when out of scope).
 * @property get - Reads the item's value(s) for this facet; a non-array return
 *   counts as a single value.
 * @property mode - `any`: item matches one selected value. `all`: item carries
 *   every selected value.
 */
export interface Facet<TItem, TState, TValue = unknown> {
  sel: (state: TState) => ReadonlySet<TValue> | undefined
  get: (item: TItem) => TValue | readonly TValue[]
  mode: FacetMode
}

/**
 * Evaluates every facet against an item, ANDed together.
 * An empty or skipped selection passes.
 */
export function applyFacets<TItem, TState>(
  item: TItem,
  state: TState,
  facets: readonly Facet<TItem, TState>[],
): boolean {
  for (const facet of facets) {
    const selection = facet.sel(state)
    if (selection === undefined || selection.size === 0) continue

    const read = facet.get(item)
    const values: readonly unknown[] = Array.isArray(read) ? read : [read]

    const matched =
      facet.mode === 'any'
        ? values.some((value) => selection.has(value))
        : Array.from(selection).every((value) => values.includes(value))

    if (!matched) return false
  }

  return true
}
