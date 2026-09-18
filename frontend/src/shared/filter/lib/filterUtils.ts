export function calculateActiveFilterCount(...filterSets: ReadonlySet<unknown>[]): number {
  return filterSets.reduce((total, set) => total + set.size, 0)
}
