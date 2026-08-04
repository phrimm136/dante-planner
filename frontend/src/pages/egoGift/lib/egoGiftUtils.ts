import { EGO_GIFT_ENHANCEMENT_BASE_COSTS } from '@/shared/gameData'
import type { EGOGiftRecipe, MixedRecipe } from '../types/EGOGiftTypes'

/**
 * Calculate enhancement cost for a given tier and level
 * @param tier - Gift tier ('1', '2', '3', '4', '5', 'EX')
 * @param level - Enhancement level (0 = base, 1 = +, 2 = ++)
 * @returns Cost in dungeon currency, or null if enhancement not available
 */
export function calculateEnhancementCost(tier: string, level: number): number | null {
  // No enhancement for level 0 (base) or tier 5/EX
  if (level === 0 || tier === '5' || tier === 'EX') {
    return null
  }

  const baseCost = EGO_GIFT_ENHANCEMENT_BASE_COSTS[tier]
  if (!baseCost) return null

  // Level 1 = base cost, Level 2 = double base cost
  return baseCost * level
}

/**
 * Extract tier string from gift tag array
 * @param tags - Array of tags (e.g., ['TIER_2', 'SOME_OTHER_TAG'])
 * @returns Tier string ('1', '2', '3', '4', '5', 'EX')
 * @throws Error if no TIER_ tag found
 */
export function extractEGOGiftTier(tags: string[]): string {
  const exTier = tags.find((t) => t === 'TIER_EX')
  if (exTier) return 'EX'

  const tierTag = tags.find((t) => t.startsWith('TIER_'))
  if (!tierTag) {
    throw new Error(`No TIER_ tag found in tags: ${tags.join(', ')}`)
  }

  return tierTag.replace('TIER_', '')
}

/**
 * Whether a recipe is the two-pool "mixed" shape rather than the standard
 * material-list shape.
 *
 * @param recipe - Recipe to classify
 * @returns True when the recipe carries the mixed discriminant
 */
export function isMixedRecipe(recipe: EGOGiftRecipe): recipe is MixedRecipe {
  return 'type' in recipe && recipe.type === 'mixed'
}
