import { EGO_GIFT_ENHANCEMENT_BASE_COSTS, ENHANCEMENT_LEVELS } from './constants'

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
 * Get enhancement levels that should be disabled
 * A level is disabled if its description is empty or missing
 * @param descs - Array of description strings (index 0 = base, 1 = +, 2 = ++)
 * @returns Array of disabled level indices (0, 1, and/or 2)
 */
export function getDisabledEnhancementLevels(descs: string[]): number[] {
  return ENHANCEMENT_LEVELS.filter(
    (level) => level >= descs.length || !descs[level]?.trim()
  )
}

/**
 * Get maximum enhancement level from descriptions array
 * @param descs - Array of description strings (index 0 = base, 1 = +, 2 = ++)
 * @returns Maximum available enhancement level (0, 1, or 2)
 */
export function getMaxEnhancementLevel(descs: string[]): 0 | 1 | 2 {
  // Find the highest level with a valid description
  for (let i = 2; i >= 0; i--) {
    if (i < descs.length && descs[i]?.trim()) {
      return i as 0 | 1 | 2
    }
  }
  return 0
}
