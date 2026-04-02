/**
 * themePackFilter.ts
 *
 * Utility functions for theme pack filtering logic.
 */

import type { DungeonIdx, ThemePackFloor } from '@/lib/constants'
import type { ThemePackEntry } from '@/types/ThemePackTypes'

/**
 * Check if a theme pack matches the selected dungeon difficulties filter.
 * A pack matches if ALL selected difficulties appear in its exceptionConditions.
 * Empty selection = no filter (returns true).
 */
export function matchesDungeonDifficultyFilter(
  entry: ThemePackEntry,
  selectedDifficulties: Set<DungeonIdx>
): boolean {
  if (selectedDifficulties.size === 0) return true
  const packDifficulties = new Set(entry.exceptionConditions.map((c) => c.dungeonIdx))
  return Array.from(selectedDifficulties).every((d) => packDifficulties.has(d))
}

/**
 * Check if a theme pack matches the selected floors filter.
 * A pack matches if ALL selected floors appear in its exceptionConditions.
 * Packs with undefined selectableFloors (infinity/extreme) never match floor filters.
 * Empty selection = no filter (returns true).
 */
export function matchesFloorFilter(
  entry: ThemePackEntry,
  selectedFloors: Set<ThemePackFloor>
): boolean {
  if (selectedFloors.size === 0) return true
  const packFloors = new Set<number>()
  for (const cond of entry.exceptionConditions) {
    for (const f of cond.selectableFloors ?? []) {
      packFloors.add(f)
    }
  }
  return Array.from(selectedFloors).every((f) => packFloors.has(f))
}

/**
 * Check if a theme pack matches the selected ego gifts filter.
 * A pack matches if ANY of its specificEgoGiftPool IDs is in the selected set.
 * Empty selection = no filter (returns true).
 */
export function matchesEgoGiftFilter(
  entry: ThemePackEntry,
  selectedEgoGifts: Set<string>
): boolean {
  if (selectedEgoGifts.size === 0) return true
  if (entry.specificEgoGiftPool.some((giftId) => selectedEgoGifts.has(String(giftId)))) return true
  if (entry.fixedRewardEgoGifts?.some((giftId) => selectedEgoGifts.has(String(giftId)))) return true
  return false
}
