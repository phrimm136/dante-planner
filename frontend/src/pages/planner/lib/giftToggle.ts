import { buildSelectionLookup, encodeGiftSelection, getCascadeIngredients } from '@/pages/egoGift'

import type { EGOGiftSpec } from '@/pages/egoGift'
import type { EnhancementLevel } from '@/shared/gameData'

/** What the toggle cannot read off the selection itself. */
export interface GiftToggleOptions {
  /** Gift specs keyed by base gift id, read for the recipe cascade. */
  specById: ReadonlyMap<string, EGOGiftSpec>
  /**
   * Whether a cascaded ingredient may join the selection. A selector that can
   * reach every gift leaves it out; one restricted to a theme pack supplies it.
   */
  canCascade?: (ingredientId: string) => boolean
}

/**
 * Select a gift at an enhancement level, or drop it when that level is already
 * the selected one.
 *
 * Selecting a gift nothing has selected yet also selects the ingredients its
 * recipe fuses, so the selection carries what building it consumes. Changing the
 * enhancement of a selected gift cascades nothing: its ingredients were settled
 * when it was first selected, and re-adding them would undo a later deselection.
 */
export function applyGiftToggle(
  selected: Set<string>,
  giftId: string,
  enhancement: EnhancementLevel,
  { specById, canCascade }: GiftToggleOptions,
): Set<string> {
  const selectionLookup = buildSelectionLookup(selected)
  const existing = selectionLookup.get(giftId)
  const next = new Set(selected)

  if (existing) {
    next.delete(existing.encodedId)
    if (existing.enhancement !== enhancement) {
      next.add(encodeGiftSelection(enhancement, giftId))
    }
    return next
  }

  next.add(encodeGiftSelection(enhancement, giftId))

  const giftSpec = specById.get(giftId)
  if (!giftSpec) return next

  const visited = new Set<string>([giftId])

  for (const ingredientId of getCascadeIngredients(giftSpec.recipe)) {
    const id = String(ingredientId)
    if (visited.has(id)) continue
    visited.add(id)

    if (canCascade && !canCascade(id)) continue
    if (!selectionLookup.has(id)) {
      next.add(encodeGiftSelection(0, id))
    }
  }

  return next
}
