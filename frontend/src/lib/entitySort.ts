import { EGO_TYPES } from '@/lib/constants'
import type { EGOType } from '@/types/EGOTypes'

/**
 * Sort Identity entities by release date
 * Sort order: updateDate DESC -> rank DESC -> id DESC
 */
export function sortByReleaseDate<T extends { updateDate: number; rank: number; id: string }>(
  items: T[]
): T[] {
  return [...items].sort((a, b) => {
    if (a.updateDate !== b.updateDate) return b.updateDate - a.updateDate
    if (a.rank !== b.rank) return b.rank - a.rank
    return parseInt(b.id, 10) - parseInt(a.id, 10)
  })
}

/**
 * Sort EGO entities by release date, ego type tier, sinner, then id
 * Sort order: updateDate DESC -> egoType tier DESC -> sinner DESC -> id DESC
 *
 * EGO type tier uses EGO_TYPES index (ALEPH=4 highest, ZAYIN=0 lowest)
 * Sinner extracted from id (format: 2XXYY where XX = sinner 01-12)
 */
export function sortEGOByDate<T extends { updateDate: number; egoType: EGOType; id: string }>(
  items: T[]
): T[] {
  return [...items].sort((a, b) => {
    // Primary: updateDate descending (newer first)
    if (a.updateDate !== b.updateDate) return b.updateDate - a.updateDate
    // Secondary: egoType tier descending (ALEPH > WAW > HE > TETH > ZAYIN)
    const tierA = EGO_TYPES.indexOf(a.egoType)
    const tierB = EGO_TYPES.indexOf(b.egoType)
    if (tierA !== tierB) return tierB - tierA
    // Tertiary: sinner descending (sinner 12 > sinner 01)
    const sinnerA = parseInt(a.id.substring(1, 3), 10)
    const sinnerB = parseInt(b.id.substring(1, 3), 10)
    if (sinnerA !== sinnerB) return sinnerB - sinnerA
    // Quaternary: id descending (higher id first within same sinner)
    return parseInt(b.id, 10) - parseInt(a.id, 10)
  })
}
