import { EGO_GIFT_ENHANCEMENT_BASE_COSTS } from '@/shared/gameData'
import type { EGOGiftRecipe, MixedRecipe } from '../types/EGOGiftTypes'

export function calculateEnhancementCost(tier: string, level: number): number | null {
  if (level === 0 || tier === '5' || tier === 'EX') {
    return null
  }

  const baseCost = EGO_GIFT_ENHANCEMENT_BASE_COSTS[tier]
  if (!baseCost) return null

  return baseCost * level
}

export function isMixedRecipe(recipe: EGOGiftRecipe): recipe is MixedRecipe {
  return 'type' in recipe && recipe.type === 'mixed'
}
