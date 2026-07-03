/**
 * Set Utilities
 *
 * Pure functions for Set comparisons.
 */

/**
 * Compares two Sets by content (same size and same members).
 *
 * @example
 * areSetsEqual(new Set(['a']), new Set(['a'])) // true
 * areSetsEqual(new Set(['a']), new Set(['b'])) // false
 */
export function areSetsEqual<T>(a: ReadonlySet<T>, b: ReadonlySet<T>): boolean {
  if (a.size !== b.size) return false
  for (const item of a) {
    if (!b.has(item)) return false
  }
  return true
}
