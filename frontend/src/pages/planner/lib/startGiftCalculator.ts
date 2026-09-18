import type { StartBuff } from '@/shared/gameText'
import { getBuffById } from './startBuffs'

export function calculateMaxGiftSelection(
  buffs: StartBuff[] | undefined,
  selectedIds: Set<number>,
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
