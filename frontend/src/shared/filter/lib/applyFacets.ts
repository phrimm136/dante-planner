export type FacetMode = 'any' | 'all'

export interface Facet<TItem, TState, TValue = unknown> {
  sel: (state: TState) => ReadonlySet<TValue> | undefined
  get: (item: TItem) => TValue | readonly TValue[]
  mode: FacetMode
}

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
