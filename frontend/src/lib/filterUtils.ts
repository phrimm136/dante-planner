/**
 * Filter Utilities
 *
 * Pure functions for filter-related calculations.
 */

/**
 * Calculates the total number of active filters across multiple filter sets
 *
 * @param filterSets - Variable number of ReadonlySet<unknown> filter sets
 * @returns Sum of all set sizes (0 if no arguments provided)
 *
 * @example
 * const sinners = new Set(['1', '2'])
 * const seasons = new Set([1, 2, 3])
 * calculateActiveFilterCount(sinners, seasons) // Returns 5
 */
export function calculateActiveFilterCount(
  ...filterSets: ReadonlySet<unknown>[]
): number {
  return filterSets.reduce((total, set) => total + set.size, 0)
}
