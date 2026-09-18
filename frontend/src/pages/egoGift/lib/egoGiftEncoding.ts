import { GIFT_ID_LENGTH, EGOGiftIdSchema, EncodedGiftIdSchema } from '@/shared/gameData'
import type { EGOGiftId, EncodedGiftId } from '@/shared/gameData'
import type { EnhancementLevel } from '@/shared/gameData'
import type { EGOGiftRecipe, EGOGiftEntity, EGOGiftSpec } from '@/pages/egoGift'
import type { SortMode } from '@/shared/filter'
import { sortEGOGifts } from './egoGiftSort'
import { isMixedRecipe } from './egoGiftUtils'
import { toEGOGiftEntity } from './egoGiftEntity'

/**
 * Encodes a gift selection into a numeric string format
 * Format: enhancement digit + giftId (e.g., 19001 = level 1 + gift 9001)
 * When enhancement is 0, returns just the giftId (e.g., 9002)
 *
 * @param enhancement - Enhancement level (0, 1, or 2)
 * @param giftId - Gift ID string
 * @returns Encoded numeric string
 */
export function encodeGiftSelection(
  enhancement: EnhancementLevel,
  giftId: EGOGiftId,
): EncodedGiftId {
  if (enhancement === 0) {
    return EncodedGiftIdSchema.parse(giftId)
  }
  return EncodedGiftIdSchema.parse(`${enhancement}${giftId}`)
}

export interface GiftSelection {
  enhancement: EnhancementLevel
  giftId: EGOGiftId
}

/**
 * Decodes an encoded gift selection into enhancement level and gift ID
 * Handles both enhanced (19001) and base (9001) formats
 *
 * @param encodedId - Encoded gift selection
 * @returns Decoded selection
 */
export function decodeGiftSelection(encodedId: EncodedGiftId): GiftSelection {
  const giftId = EGOGiftIdSchema.parse(encodedId.slice(-GIFT_ID_LENGTH))
  const enhancement =
    encodedId.length === GIFT_ID_LENGTH ? 0 : (Number(encodedId[0]) as EnhancementLevel)
  return { enhancement, giftId }
}

export function getBaseGiftId(encodedId: EncodedGiftId): EGOGiftId {
  return decodeGiftSelection(encodedId).giftId
}

export function findEncodedGiftId(
  giftId: EGOGiftId,
  selectedIds: ReadonlySet<EncodedGiftId>,
): EncodedGiftId | undefined {
  for (const encodedId of selectedIds) {
    if (getBaseGiftId(encodedId) === giftId) {
      return encodedId
    }
  }
  return undefined
}

export interface GiftSelectionEntry {
  encodedId: EncodedGiftId
  enhancement: EnhancementLevel
}

export function buildSelectionLookup(
  selectedIds: ReadonlySet<EncodedGiftId>,
): Map<EGOGiftId, GiftSelectionEntry> {
  const map = new Map<EGOGiftId, GiftSelectionEntry>()
  for (const encodedId of selectedIds) {
    const decoded = decodeGiftSelection(encodedId)
    map.set(decoded.giftId, { encodedId, enhancement: decoded.enhancement })
  }
  return map
}

export interface DecodedGiftSelection {
  encodedId: EncodedGiftId
  item: EGOGiftEntity & { name: string }
  enhancement: EnhancementLevel
}

/**
 * Resolve encoded gift selections against the spec and i18n catalogues.
 *
 * Selections that do not decode, or whose gift the spec does not carry, are
 * dropped — a planner may hold ids from a content version this build predates.
 *
 * @param encodedIds - Encoded gift selection strings
 * @param spec - Gift specs keyed by base gift ID
 * @param i18n - Gift names keyed by base gift ID
 * @returns One entry per resolvable selection, in iteration order
 */
export function decodeGiftSelections(
  encodedIds: Iterable<EncodedGiftId>,
  spec: Record<EGOGiftId, EGOGiftSpec>,
  i18n: Record<EGOGiftId, string>,
): DecodedGiftSelection[] {
  const decoded: DecodedGiftSelection[] = []

  for (const encodedId of encodedIds) {
    const { giftId, enhancement } = decodeGiftSelection(encodedId)
    const giftSpec = spec[giftId]
    if (!giftSpec) continue

    const name = i18n[giftId] || giftId
    decoded.push({
      encodedId,
      enhancement,
      item: { ...toEGOGiftEntity(giftId, giftSpec), name },
    })
  }

  return decoded
}

export function orderSelectionsByGiftOrder(
  selections: DecodedGiftSelection[],
  sortMode: SortMode,
): DecodedGiftSelection[] {
  const byGiftId = new Map(selections.map((selection) => [selection.item.id, selection]))
  return sortEGOGifts(
    selections.map((selection) => selection.item),
    sortMode,
  ).map((item) => byGiftId.get(item.id)!)
}

export function decodeAndOrderGiftSelections(
  encodedIds: Iterable<EncodedGiftId>,
  spec: Record<EGOGiftId, EGOGiftSpec>,
  i18n: Record<EGOGiftId, string>,
  sortMode: SortMode,
): DecodedGiftSelection[] {
  return orderSelectionsByGiftOrder(decodeGiftSelections(encodedIds, spec, i18n), sortMode)
}

export function lookupByGiftId<T>(
  encodedId: EncodedGiftId,
  byGiftId: Record<EGOGiftId, T>,
): T | undefined {
  return byGiftId[getBaseGiftId(encodedId)]
}

export function hasGiftId(encodedId: EncodedGiftId, byGiftId: Record<EGOGiftId, unknown>): boolean {
  return getBaseGiftId(encodedId) in byGiftId
}

export function giftDisplayName(encodedId: EncodedGiftId, i18n: Record<EGOGiftId, string>): string {
  return lookupByGiftId(encodedId, i18n) ?? encodedId
}

export function getCascadeIngredients(recipe: EGOGiftRecipe | undefined): EGOGiftId[] {
  if (!recipe) return []

  if (isMixedRecipe(recipe)) return []

  if ('materials' in recipe) {
    const uniqueIds = new Set<EGOGiftId>()
    recipe.materials.forEach((option) => option.forEach((id) => uniqueIds.add(id)))
    return Array.from(uniqueIds)
  }

  return []
}
