/**
 * Planner Domain Rules
 *
 * Pure domain predicates shared by planner validators and UI: EGO Gift
 * theme-pack affordability and floor theme-pack prerequisite gating.
 */

import { getBaseGiftId } from '@/lib/egoGiftEncoding'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { EGOGiftSpec } from '@/pages/egoGift'

/**
 * Check if an EGO Gift is affordable for a specific theme pack
 *
 * @param gift - EGO Gift spec data
 * @param themePackId - Theme pack ID to check
 * @returns true if gift is available for this theme pack
 *
 * Rules:
 * - If gift.themePack is empty array → available in ALL theme packs (universal)
 * - If gift.themePack has values → only available in those specific packs
 */
export function isGiftAffordableForThemePack(gift: EGOGiftSpec, themePackId: string): boolean {
  return gift.themePack.length === 0 || gift.themePack.includes(themePackId)
}

/**
 * Get list of unaffordable gift IDs for a specific floor and theme pack
 *
 * @param giftIds - Selected gift IDs for this floor
 * @param themePackId - Theme pack ID for this floor
 * @param egoGiftSpec - EGO Gift spec data
 * @returns Array of gift IDs that are not affordable for this theme pack
 */
export function getUnaffordableGiftIds(
  giftIds: Set<string>,
  themePackId: string,
  egoGiftSpec: Record<string, EGOGiftSpec>
): string[] {
  return Array.from(giftIds).filter((giftId) => {
    const baseGiftId = getBaseGiftId(giftId)
    const gift = egoGiftSpec[baseGiftId]
    if (!gift) return false
    return !isGiftAffordableForThemePack(gift, themePackId)
  })
}

/**
 * Get list of unaffordable gift names for a specific floor and theme pack
 *
 * @param giftIds - Selected gift IDs for this floor
 * @param themePackId - Theme pack ID for this floor
 * @param egoGiftSpec - EGO Gift spec data
 * @param egoGiftI18n - EGO Gift i18n name map
 * @returns Object with arrays of unaffordable gift IDs and their names
 */
export function getUnaffordableGiftNames(
  giftIds: Set<string>,
  themePackId: string,
  egoGiftSpec: Record<string, EGOGiftSpec>,
  egoGiftI18n: Record<string, string>
): { ids: string[]; names: string[] } {
  const ids = getUnaffordableGiftIds(giftIds, themePackId, egoGiftSpec)
  const names = ids.map(id => egoGiftI18n[getBaseGiftId(id)] ?? id)
  return { ids, names }
}

/**
 * Checks if a floor's theme pack selector should be enabled based on previous floor state
 *
 * Rules:
 * - Floor 1 (index 0): Always enabled (no prerequisites)
 * - Floors 2-15: Enabled only if the previous floor has a theme pack selected
 *
 * @param floorIndex - 0-indexed floor position (0 for floor 1, 1 for floor 2, etc.)
 * @param floorSelections - Array of all floor selections
 * @returns true if theme pack selector should be enabled, false if disabled
 *
 * @example
 * // Floor 1 is always enabled
 * canSelectFloorThemePack(0, floors) // Returns true
 *
 * @example
 * // Floor 2 enabled only if floor 1 has theme pack
 * const floors = [{ themePackId: '123', ... }, { themePackId: null, ... }]
 * canSelectFloorThemePack(1, floors) // Returns true (floor 1 has pack)
 *
 * @example
 * // Floor 3 disabled if floor 2 has no theme pack
 * const floors = [{ ... }, { themePackId: null, ... }, { ... }]
 * canSelectFloorThemePack(2, floors) // Returns false (floor 2 has no pack)
 */
export function canSelectFloorThemePack(
  floorIndex: number,
  floorSelections: FloorThemeSelection[]
): boolean {
  // First floor always enabled
  if (floorIndex === 0) return true

  // Other floors require previous floor to have a theme pack
  return floorSelections[floorIndex - 1].themePackId !== null
}
