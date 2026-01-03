import type { EnhancementLevel } from './constants'
import type { EGOGiftRecipe } from '@/types/EGOGiftTypes'

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
 * Decodes an encoded gift selection string into enhancement level and gift ID
 * Handles both enhanced (19001) and base (9001) formats
 *
 * @param encodedId - Encoded gift selection string
 * @returns Object with enhancement level and gift ID
 */
export function decodeGiftSelection(encodedId: string): {
  enhancement: EnhancementLevel
  giftId: string
} {
  // Gift IDs are 4 digits (9xxx format), so:
  // - 4 digits = base gift (enhancement 0)
  // - 5 digits = enhanced gift (first digit is enhancement level)
  if (encodedId.length === 4) {
    return { enhancement: 0, giftId: encodedId }
  }

  const enhancementDigit = parseInt(encodedId[0], 10)
  const giftId = encodedId.slice(1)

  // Validate enhancement level
  if (enhancementDigit === 1 || enhancementDigit === 2) {
    return { enhancement: enhancementDigit, giftId }
  }

  // Fallback: treat as base gift if parsing fails
  return { enhancement: 0, giftId: encodedId }
}

/**
 * Extracts the base gift ID from an encoded selection
 * Useful for checking if a gift is selected regardless of enhancement
 *
 * @param encodedId - Encoded gift selection string
 * @returns Base gift ID
 */
export function getBaseGiftId(encodedId: string): string {
  return decodeGiftSelection(encodedId).giftId
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
    if (decoded.giftId === giftId) {
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
 * Use with useMemo to cache the result and avoid O(n) iteration per gift card
 *
 * @example
 * const selectionMap = useMemo(() => buildSelectionLookup(selectedGiftIds), [selectedGiftIds])
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
    const { giftId, enhancement } = decodeGiftSelection(encodedId)
    map.set(giftId, { encodedId, enhancement })
  }
  return map
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
  if ('type' in recipe && recipe.type === 'mixed') return []

  // Standard recipe: union all materials across all recipe options
  const uniqueIds = new Set<number>()
  recipe.materials.forEach((option) => option.forEach((id) => uniqueIds.add(id)))
  return Array.from(uniqueIds)
}
