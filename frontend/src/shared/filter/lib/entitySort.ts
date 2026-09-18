import { EGO_TYPES, type EgoType } from '@/shared/gameData'

export function entriesSortedById<TEntry>(spec: Record<string, TEntry>): [string, TEntry][] {
  return Object.entries(spec).sort(([a], [b]) => a.localeCompare(b))
}

export function sortByReleaseDate<T extends { updateDate: number; rank: number; id: string }>(
  items: T[],
): T[] {
  return [...items].sort((a, b) => {
    if (a.updateDate !== b.updateDate) return b.updateDate - a.updateDate
    if (a.rank !== b.rank) return b.rank - a.rank
    return parseInt(b.id, 10) - parseInt(a.id, 10)
  })
}

export function sortEGOByDate<T extends { updateDate: number; egoType: EgoType; id: string }>(
  items: T[],
): T[] {
  return [...items].sort((a, b) => {
    if (a.updateDate !== b.updateDate) return b.updateDate - a.updateDate
    const tierA = EGO_TYPES.indexOf(a.egoType)
    const tierB = EGO_TYPES.indexOf(b.egoType)
    if (tierA !== tierB) return tierB - tierA
    const sinnerA = parseInt(a.id.substring(1, 3), 10)
    const sinnerB = parseInt(b.id.substring(1, 3), 10)
    if (sinnerA !== sinnerB) return sinnerB - sinnerA
    return parseInt(b.id, 10) - parseInt(a.id, 10)
  })
}
