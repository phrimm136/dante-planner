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

/**
 * A decoded gift selection
 */
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

/**
 * Extracts the base gift ID from an encoded selection
 * Useful for checking if a gift is selected regardless of enhancement
 *
 * @param encodedId - Encoded gift selection
 * @returns Base gift ID
 */
export function getBaseGiftId(encodedId: EncodedGiftId): EGOGiftId {
  return decodeGiftSelection(encodedId).giftId
}

/**
 * Finds the encoded ID for a specific gift in a set of selections
 * Returns undefined if not found
 *
 * @param giftId - Gift ID to find
 * @param selectedIds - Set of encoded selection strings
 * @returns Encoded selection string or undefined
 */
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

/**
 * Selection lookup entry for O(1) gift status checks
 */
export interface GiftSelectionEntry {
  encodedId: EncodedGiftId
  enhancement: EnhancementLevel
}

/**
 * Builds a Map from giftId to selection data for O(1) lookups
 *
 * @example
 * const selectionMap = buildSelectionLookup(selectedGiftIds)
 * const entry = selectionMap.get(giftId)
 * const isSelected = entry !== undefined
 * const enhancement = entry?.enhancement ?? 0
 *
 * @param selectedIds - Set of encoded selection strings
 * @returns Map from giftId to selection entry
 */
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

/**
 * A selected gift resolved against the spec, with the enhancement it was
 * selected at. The name is always resolved — untranslated gifts carry their id.
 */
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

/**
 * Order decoded selections by the shared gift ordering, keeping each item paired
 * with the enhancement it was selected at.
 *
 * @param selections - Decoded selections
 * @param sortMode - Ordering to apply
 * @returns A new ordered array
 */
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

/**
 * Resolve encoded selections and order them in one step — the shape every
 * viewer needs, so neither half is called alone at a call site.
 *
 * @param encodedIds - Encoded gift selection strings
 * @param spec - Gift specs keyed by base gift ID
 * @param i18n - Gift names keyed by base gift ID
 * @param sortMode - Ordering to apply
 * @returns Resolved selections in the shared gift order
 */
export function decodeAndOrderGiftSelections(
  encodedIds: Iterable<EncodedGiftId>,
  spec: Record<EGOGiftId, EGOGiftSpec>,
  i18n: Record<EGOGiftId, string>,
  sortMode: SortMode,
): DecodedGiftSelection[] {
  return orderSelectionsByGiftOrder(decodeGiftSelections(encodedIds, spec, i18n), sortMode)
}

/**
 * Look an encoded selection up in a map keyed by base gift ID.
 *
 * @param encodedId - Encoded gift selection string
 * @param byGiftId - Map keyed by base gift ID
 * @returns The entry, or undefined when the encoding is invalid or absent
 */
export function lookupByGiftId<T>(
  encodedId: EncodedGiftId,
  byGiftId: Record<EGOGiftId, T>,
): T | undefined {
  return byGiftId[getBaseGiftId(encodedId)]
}

/**
 * Whether a map keyed by base gift ID carries an entry for this encoded selection.
 *
 * @param encodedId - Encoded gift selection string
 * @param byGiftId - Map keyed by base gift ID
 * @returns False when the encoding is invalid or the key is absent
 */
export function hasGiftId(encodedId: EncodedGiftId, byGiftId: Record<EGOGiftId, unknown>): boolean {
  return getBaseGiftId(encodedId) in byGiftId
}

/**
 * Localized name for an encoded selection, falling back to the id itself when
 * the encoding is invalid or untranslated.
 *
 * @param encodedId - Encoded gift selection string
 * @param i18n - Gift names keyed by base gift ID
 * @returns Display name
 */
export function giftDisplayName(encodedId: EncodedGiftId, i18n: Record<EGOGiftId, string>): string {
  return lookupByGiftId(encodedId, i18n) ?? encodedId
}

/**
 * Extracts all unique ingredient IDs from a recipe for cascade selection
 * For standard recipes: unions all materials across all recipe options
 * For mixed recipes (Lunar Memory): returns empty array (requires manual selection)
 *
 * @param recipe - Recipe object or undefined
 * @returns Array of ingredient gift IDs to cascade-select
 */
export function getCascadeIngredients(recipe: EGOGiftRecipe | undefined): EGOGiftId[] {
  if (!recipe) return []

  // Mixed recipe (Lunar Memory): skip cascade, user must manually select
  if (isMixedRecipe(recipe)) return []

  // Standard recipe: union all materials across all recipe options
  if ('materials' in recipe) {
    const uniqueIds = new Set<EGOGiftId>()
    recipe.materials.forEach((option) => option.forEach((id) => uniqueIds.add(id)))
    return Array.from(uniqueIds)
  }

  return []
}
