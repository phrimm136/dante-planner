import { startTransition } from 'react'
import { useStartBuffListSpec, useStartBuffListI18n } from './useStartBuffList'
import { getBaseBuffs, toStartBuffs } from '../lib/startBuffs'
import { useBattleKeywords } from '@/shared/gameText'
import type { MDVersion } from '@/shared/gameData'
import type { StartBuff, StartBuffI18n, BattleKeywords } from '@/shared/gameText'
import { deriveEnhancements, getBaseIdFromBuffId, createBuffId } from '@/shared/gameText'
import type { EnhancementLevel } from '@/shared/gameText'

export interface UseStartBuffSelectionResult {
  buffs: StartBuff[]
  i18n: StartBuffI18n
  battleKeywords: BattleKeywords
  displayBuffs: StartBuff[]
  handleSelect: (buffId: number, selected: boolean) => void
}

export function useStartBuffSelection(
  mdVersion: MDVersion,
  selectedBuffIds: Set<number>,
  onSelectionChange: (buffIds: Set<number>) => void,
): UseStartBuffSelectionResult {
  const i18n = useStartBuffListI18n(mdVersion)
  const buffs = toStartBuffs(useStartBuffListSpec(mdVersion), i18n)
  const { data: battleKeywords } = useBattleKeywords()

  const enhancements = deriveEnhancements(selectedBuffIds)
  const baseBuffs = getBaseBuffs(buffs)

  const displayBuffs = baseBuffs.map((baseBuff) => {
    const enhancement = enhancements[baseBuff.baseId] ?? 0
    const displayBuffId = createBuffId(baseBuff.baseId, enhancement)
    const displayBuff = buffs.find((b) => Number(b.id) === displayBuffId)
    return displayBuff ?? baseBuff
  })

  const handleSelect = (buffId: number, selected: boolean) => {
    startTransition(() => {
      const baseId = getBaseIdFromBuffId(buffId)

      const newSelection = new Set(selectedBuffIds)

      for (let level = 0; level <= 2; level++) {
        newSelection.delete(createBuffId(baseId, level as EnhancementLevel))
      }

      if (selected) {
        newSelection.add(buffId)
      }

      onSelectionChange(newSelection)
    })
  }

  return {
    buffs,
    i18n,
    battleKeywords,
    displayBuffs,
    handleSelect,
  }
}
