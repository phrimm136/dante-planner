/**
 * themePackFusion.ts
 *
 * Which fusion gifts a theme pack's gift pool can produce on its own.
 */

import { isMixedRecipe } from '@/pages/egoGift'
import type { EGOGiftSpec } from '@/pages/egoGift'

/**
 * Ids of gifts outside `giftIds` whose recipe the pool covers entirely.
 * A mixed recipe needs every id of both pools; a standard recipe needs one of
 * its material sets.
 */
export function findFusionGifts(
  spec: Record<string, EGOGiftSpec>,
  giftIds: readonly string[],
): string[] {
  const pool = new Set(giftIds)
  const fusionIds: string[] = []

  for (const [id, giftSpec] of Object.entries(spec)) {
    if (pool.has(id)) continue

    const recipe = giftSpec.recipe
    if (!recipe) continue

    if (isMixedRecipe(recipe)) {
      const materials = [...(recipe.a?.ids ?? []), ...(recipe.b?.ids ?? [])]
      if (materials.length > 0 && materials.every((mid) => pool.has(String(mid)))) {
        fusionIds.push(id)
      }
      continue
    }

    if ('materials' in recipe) {
      for (const materials of recipe.materials ?? []) {
        if (materials.length > 0 && materials.every((mid) => pool.has(String(mid)))) {
          fusionIds.push(id)
          break
        }
      }
    }
  }

  return fusionIds
}
