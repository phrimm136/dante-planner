import { startTransition } from 'react'
import { useStartBuffData, getBaseBuffs } from '@/hooks/useStartBuffData'
import { useBattleKeywords } from '@/hooks/useBattleKeywords'
import type { MDVersion } from '@/hooks/useStartBuffData'
import type { StartBuff, StartBuffI18n, BattleKeywords } from '@/types/StartBuffTypes'
import {
  deriveEnhancements,
  getBaseIdFromBuffId,
  createBuffId,
} from '@/types/StartBuffTypes'
import type { EnhancementLevel } from '@/types/StartBuffTypes'

/**
 * Return type for useStartBuffSelection hook
 */
export interface UseStartBuffSelectionResult {
  /** All buffs for the current MD version */
  buffs: StartBuff[]
  /** i18n translations */
  i18n: StartBuffI18n
  /** Battle keywords for tooltip display */
  battleKeywords: BattleKeywords
  /** Buffs to display (base buffs with current enhancement level applied) */
  displayBuffs: StartBuff[]
  /** Handler for buff selection/deselection */
  handleSelect: (buffId: number) => void
}

/**
 * Hook that provides start buff selection logic
 * Encapsulates displayBuffs calculation and handleSelect behavior
 *
 * @param mdVersion - Mirror Dungeon version (5 or 6)
 * @param selectedBuffIds - Currently selected buff IDs
 * @param onSelectionChange - Callback when selection changes
 *
 * @example
 * ```tsx
 * function BuffSelector({ mdVersion, selectedBuffIds, onSelectionChange }) {
 *   const { displayBuffs, handleSelect, buffs, i18n, battleKeywords } =
 *     useStartBuffSelection(mdVersion, selectedBuffIds, onSelectionChange)
 *
 *   return displayBuffs.map(buff => (
 *     <StartBuffCard
 *       key={buff.baseId}
 *       buff={buff}
 *       allBuffs={buffs}
 *       i18n={i18n}
 *       battleKeywords={battleKeywords}
 *       isSelected={selectedBuffIds.has(Number(buff.id))}
 *       onSelect={handleSelect}
 *     />
 *   ))
 * }
 * ```
 */
export function useStartBuffSelection(
  mdVersion: MDVersion,
  selectedBuffIds: Set<number>,
  onSelectionChange: (buffIds: Set<number>) => void
): UseStartBuffSelectionResult {
  const { data: buffs, i18n } = useStartBuffData(mdVersion)
  const { data: battleKeywords } = useBattleKeywords()

  const enhancements = deriveEnhancements(selectedBuffIds)
  const baseBuffs = getBaseBuffs(buffs)

  const displayBuffs = baseBuffs.map(baseBuff => {
    const enhancement = enhancements[baseBuff.baseId] ?? 0
    const displayBuffId = createBuffId(baseBuff.baseId, enhancement)
    const displayBuff = buffs.find(b => Number(b.id) === displayBuffId)
    return displayBuff ?? baseBuff
  })

  const handleSelect = (buffId: number) => {
    startTransition(() => {
      const isDeselect = buffId < 0
      const actualBuffId = Math.abs(buffId)
      const baseId = getBaseIdFromBuffId(actualBuffId)

      const newSelection = new Set(selectedBuffIds)

      // Remove any existing selection for this base buff (any enhancement level)
      for (let level = 0; level <= 2; level++) {
        newSelection.delete(createBuffId(baseId, level as EnhancementLevel))
      }

      if (!isDeselect) {
        newSelection.add(actualBuffId)
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
