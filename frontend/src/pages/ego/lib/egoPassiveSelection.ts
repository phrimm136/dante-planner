/**
 * EGO Passive Selection
 *
 * Pure functions for choosing which EGO passives are "effective" (visible
 * normally) vs "locked" (dimmed preview of future tiers) at a given threadspin.
 *
 * EGO dedupes by a `slot key` derived from the ID (everything except the last
 * digit). Two passives that differ only in the last digit (e.g. `2040211`
 * "active at threadspin 2-4" and `2040212` "active at threadspin 5") share a
 * slot and are mutually exclusive — the higher-tier one *replaces* the
 * lower-tier one rather than appearing alongside it.
 */

import { selectEffectivePassives, selectLockedPassives } from '@/shared/passiveSelection'

/**
 * Slot key for an EGO passive — drops the trailing variant digit.
 *
 * @example
 * getEgoPassiveSlotKey('2040211') // => '204021'
 * getEgoPassiveSlotKey('2040212') // => '204021'   (same slot, different variant)
 */
export function getEgoPassiveSlotKey(passiveId: string): string {
  return passiveId.slice(0, -1)
}

/**
 * Get the effective (visible, active) EGO passives at the given threadspin.
 *
 * Empty slots inherit from the most recent non-empty slot below them,
 * so `passiveList[1]` covers threadspin 2, 3, 4 until something explicit
 * lands in `passiveList[4]`.
 */
export function getEffectiveEgoPassives(
  passiveList: string[][],
  threadspinIndex: number,
): string[] {
  return selectEffectivePassives(passiveList, threadspinIndex)
}

/**
 * Get locked passives — those from higher tiers that aren't part of the
 * effective set. A higher-tier passive that simply *replaces* an effective
 * one (same slot key) is hidden, not shown as a dimmed preview.
 */
export function getLockedEgoPassives(passiveList: string[][], threadspinIndex: number): string[] {
  return selectLockedPassives(passiveList, threadspinIndex, getEgoPassiveSlotKey)
}
