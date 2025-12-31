/**
 * Sort entities by season (descending) then id (descending)
 * Used for Identity and EGO lists to show newest releases first
 *
 * Sort order: season DESC -> id DESC (newest first)
 */
export function sortByReleaseDate<T extends { updateDate: number; id: string }>(
  items: T[]
): T[] {
  return [...items].sort((a, b) => {
    // Primary: season descending (higher updateDate = newer)
    if (a.updateDate !== b.updateDate) return b.updateDate - a.updateDate
    // Secondary: id descending (higher id = newer within same season)
    return parseInt(b.id, 10) - parseInt(a.id, 10)
  })
}
