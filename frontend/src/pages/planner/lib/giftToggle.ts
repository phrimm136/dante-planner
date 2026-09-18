import { buildSelectionLookup, encodeGiftSelection, getCascadeIngredients } from '@/pages/egoGift'

import type { EGOGiftSpec } from '@/pages/egoGift'
import type { EGOGiftId, EncodedGiftId, EnhancementLevel } from '@/shared/gameData'

export interface GiftToggleOptions {
  specById: ReadonlyMap<string, EGOGiftSpec>
  canCascade?: (ingredientId: EGOGiftId) => boolean
}

export function applyGiftToggle(
  selected: ReadonlySet<EncodedGiftId>,
  giftId: EGOGiftId,
  enhancement: EnhancementLevel,
  { specById, canCascade }: GiftToggleOptions,
): Set<EncodedGiftId> {
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
    if (visited.has(ingredientId)) continue
    visited.add(ingredientId)

    if (canCascade && !canCascade(ingredientId)) continue
    if (!selectionLookup.has(ingredientId)) {
      next.add(encodeGiftSelection(0, ingredientId))
    }
  }

  return next
}
