/**
 * abEventFilter.ts
 *
 * Utility functions for abnormality event filtering logic.
 */

import type { AbEventSpecListEntry } from '@/schemas/AbEventSchemas'

/**
 * Check if an event matches the selected ego gifts filter.
 * An event matches if ANY of its relatedEgoGifts is in the selected set.
 * Empty selection = no filter (returns true).
 */
export function matchesRelatedEgoGiftFilter(
  entry: AbEventSpecListEntry,
  selectedEgoGifts: Set<string>
): boolean {
  if (selectedEgoGifts.size === 0) return true
  return entry.relatedEgoGifts.some((giftId) => selectedEgoGifts.has(giftId))
}

/**
 * Check if an event matches the selected theme packs filter.
 * An event matches if ANY of its relatedThemePacks is in the selected set.
 * Empty selection = no filter (returns true).
 */
export function matchesRelatedThemePackFilter(
  entry: AbEventSpecListEntry,
  selectedThemePacks: Set<string>
): boolean {
  if (selectedThemePacks.size === 0) return true
  return entry.relatedThemePacks.some((packId) => selectedThemePacks.has(packId))
}
