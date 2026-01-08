/**
 * Start Gift Selection Calculator
 *
 * Pure function for calculating max gift selection based on start buff effects.
 *
 * @see StartBuffTypes.ts for StartBuff definition
 */

import type { StartBuff } from '@/types/StartBuffTypes'
import { getBuffById } from '@/hooks/useStartBuffData'

/**
 * Calculates the number of selectable start gifts based on selected buffs
 * Base = 1, plus sum of ADDITIONAL_START_EGO_GIFT_SELECT effect values
 *
 * @param buffs - Array of start buffs (from useStartBuffData)
 * @param selectedIds - Set of selected buff IDs
 * @returns Maximum number of gifts that can be selected (minimum 1)
 */
export function calculateMaxGiftSelection(
  buffs: StartBuff[] | undefined,
  selectedIds: Set<number>
): number {
  if (!buffs) return 1

  let additionalCount = 0
  for (const buffId of selectedIds) {
    const buff = getBuffById(buffs, buffId)
    if (buff) {
      for (const effect of buff.effects) {
        if (effect.type === 'ADDITIONAL_START_EGO_GIFT_SELECT' && effect.value) {
          additionalCount += effect.value
        }
      }
    }
  }

  return 1 + additionalCount
}
