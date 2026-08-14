import type { EnhancementLevel } from '@/shared/gameData'
import type { EGOGiftRecipe, EGOGiftListItem, EGOGiftSpec } from '@/pages/egoGift'
import type { SortMode } from '@/shared/filter'
import { sortEGOGifts } from './egoGiftSort'
import { isMixedRecipe } from './egoGiftUtils'

/**
 * Encodes a gift selection into a numeric string format
 * Format: enhancement digit + giftId (e.g., 19001 = level 1 + gift 9001)
 * When enhancement is 0, returns just the giftId (e.g., 9002)
 *
 * @param enhancement - Enhancement level (0, 1, or 2)
 * @param giftId - Gift ID string
 * @returns Encoded numeric string
 */
export function encodeGiftSelection(enhancement: EnhancementLevel, giftId: string): string {
  if (enhancement === 0) {
    return giftId
  }
  return `${enhancement}${giftId}`
}

/**
 * Encoded selection: an optional enhancement digit followed by the 4-digit gift ID.
 */
export const ENCODED_SELECTION_PATTERN = /^([12])?(\d{4})$/

/**
 * A decoded gift selection
 */
export interface GiftSelection {
  enhancement: EnhancementLevel
  giftId: string
}

/**
 * Decodes an encoded gift selection string into enhancement level and gift ID
 * Handles both enhanced (19001) and base (9001) formats
 *
 * @param encodedId - Encoded gift selection string
 * @returns Decoded selection, or null when the string is not a valid encoding
 */
export function decodeGiftSelection(encodedId: string): GiftSelection | null {
  const match = encodedId.match(ENCODED_SELECTION_PATTERN)
  if (!match) return null

  return {
    enhancement: match[1] ? (Number(match[1]) as EnhancementLevel) : 0,
    giftId: match[2],
  }
}

/**
 * Extracts the base gift ID from an encoded selection
 * Useful for checking if a gift is selected regardless of enhancement
 *
 * @param encodedId - Encoded gift selection string
 * @returns Base gift ID, or null when the string is not a valid encoding
 */
export function getBaseGiftId(encodedId: string): string | null {
  return decodeGiftSelection(encodedId)?.giftId ?? null
}

/**
 * Checks if a gift ID is selected in a set of encoded selections
 * Matches regardless of enhancement level
 *
 * @param giftId - Gift ID to check
 * @param selectedIds - Set of encoded selection strings
 * @returns True if the gift is selected (at any enhancement level)
 */
export function isGiftSelected(giftId: string, selectedIds: Set<string>): boolean {
  for (const encodedId of selectedIds) {
    if (getBaseGiftId(encodedId) === giftId) {
      return true
    }
  }
  return false
}

/**
 * Gets the current enhancement level for a gift from a set of selections
 * Returns 0 if not selected
 *
 * @param giftId - Gift ID to check
 * @param selectedIds - Set of encoded selection strings
 * @returns Enhancement level (0, 1, or 2)
 */
export function getGiftEnhancement(giftId: string, selectedIds: Set<string>): EnhancementLevel {
  for (const encodedId of selectedIds) {
    const decoded = decodeGiftSelection(encodedId)
    if (decoded?.giftId === giftId) {
      return decoded.enhancement
    }
  }
  return 0
}

/**
 * Finds the encoded ID for a specific gift in a set of selections
 * Returns undefined if not found
 *
 * @param giftId - Gift ID to find
 * @param selectedIds - Set of encoded selection strings
 * @returns Encoded selection string or undefined
 */
export function findEncodedGiftId(giftId: string, selectedIds: Set<string>): string | undefined {
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
  encodedId: string
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
export function buildSelectionLookup(selectedIds: Set<string>): Map<string, GiftSelectionEntry> {
  const map = new Map<string, GiftSelectionEntry>()
  for (const encodedId of selectedIds) {
    const decoded = decodeGiftSelection(encodedId)
    if (!decoded) continue
    map.set(decoded.giftId, { encodedId, enhancement: decoded.enhancement })
  }
  return map
}

/**
 * A selected gift resolved against the spec, with the enhancement it was
 * selected at. The name is always resolved — untranslated gifts carry their id.
 */
export interface DecodedGiftSelection {
  encodedId: string
  item: EGOGiftListItem & { name: string }
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
  encodedIds: Iterable<string>,
  spec: Record<string, EGOGiftSpec>,
  i18n: Record<string, string>,
): DecodedGiftSelection[] {
  const decoded: DecodedGiftSelection[] = []

  for (const encodedId of encodedIds) {
    const selection = decodeGiftSelection(encodedId)
    if (!selection) continue

    const { giftId, enhancement } = selection
    const giftSpec = spec[giftId]
    if (!giftSpec) continue

    decoded.push({
      encodedId,
      enhancement,
      item: {
        id: giftId,
        name: i18n[giftId] || giftId,
        tag: giftSpec.tag as EGOGiftListItem['tag'],
        keyword: giftSpec.keyword,
        battleKeywordList: giftSpec.battleKeywordList ?? [],
        attributeType: giftSpec.attributeType,
        themePack: giftSpec.themePack,
        maxEnhancement: giftSpec.maxEnhancement,
      },
    })
  }

  return decoded
}

/**
 * Sort decoded selections by the shared gift ordering, keeping each item paired
 * with the enhancement it was selected at.
 *
 * @param selections - Decoded selections
 * @param sortMode - Ordering to apply
 * @returns A new sorted array
 */
export function sortGiftSelections(
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
 * Look an encoded selection up in a map keyed by base gift ID.
 *
 * @param encodedId - Encoded gift selection string
 * @param byGiftId - Map keyed by base gift ID
 * @returns The entry, or undefined when the encoding is invalid or absent
 */
export function lookupByGiftId<T>(encodedId: string, byGiftId: Record<string, T>): T | undefined {
  const giftId = getBaseGiftId(encodedId)
  return giftId === null ? undefined : byGiftId[giftId]
}

/**
 * Whether a map keyed by base gift ID carries an entry for this encoded selection.
 *
 * @param encodedId - Encoded gift selection string
 * @param byGiftId - Map keyed by base gift ID
 * @returns False when the encoding is invalid or the key is absent
 */
export function hasGiftId(encodedId: string, byGiftId: Record<string, unknown>): boolean {
  const giftId = getBaseGiftId(encodedId)
  return giftId !== null && giftId in byGiftId
}

/**
 * Localized name for an encoded selection, falling back to the id itself when
 * the encoding is invalid or untranslated.
 *
 * @param encodedId - Encoded gift selection string
 * @param i18n - Gift names keyed by base gift ID
 * @returns Display name
 */
export function giftDisplayName(encodedId: string, i18n: Record<string, string>): string {
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
export function getCascadeIngredients(recipe: EGOGiftRecipe | undefined): number[] {
  if (!recipe) return []

  // Mixed recipe (Lunar Memory): skip cascade, user must manually select
  if (isMixedRecipe(recipe)) return []

  // Standard recipe: union all materials across all recipe options
  if ('materials' in recipe) {
    const uniqueIds = new Set<number>()
    recipe.materials.forEach((option: number[]) =>
      option.forEach((id: number) => uniqueIds.add(id)),
    )
    return Array.from(uniqueIds)
  }

  return []
}
