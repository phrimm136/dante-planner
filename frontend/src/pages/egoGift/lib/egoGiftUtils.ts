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
 * Whether a recipe is the two-pool "mixed" shape rather than the standard
 * material-list shape.
 *
 * @param recipe - Recipe to classify
 * @returns True when the recipe carries the mixed discriminant
 */
export function isMixedRecipe(recipe: EGOGiftRecipe): recipe is MixedRecipe {
  return 'type' in recipe && recipe.type === 'mixed'
}
