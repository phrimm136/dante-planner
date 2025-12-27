import { useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useStartBuffData, getBaseBuffs } from '@/hooks/useStartBuffData'
import { useBattleKeywords } from '@/hooks/useBattleKeywords'
import type { MDVersion } from '@/hooks/useStartBuffData'
import { StartBuffCard } from './StartBuffCard'
import { BASE_BUFF_IDS, getBaseIdFromBuffId, createBuffId, getEnhancementFromBuffId } from '@/types/StartBuffTypes'

interface StartBuffSectionProps {
  mdVersion: MDVersion
  selectedBuffIds: Set<number>
  onSelectionChange: (buffIds: Set<number>) => void
}

/**
 * Start buff section container
 * Displays 10 buff cards in a 2-row grid (5 per row)
 * Manages multi-selection state
 */
export function StartBuffSection({
  mdVersion,
  selectedBuffIds,
  onSelectionChange,
}: StartBuffSectionProps) {
  const { t } = useTranslation()
  const { data: buffs, i18n } = useStartBuffData(mdVersion)
  const { data: battleKeywords } = useBattleKeywords()

  // Track enhancement level per base buff (for display purposes)
  const [enhancements, setEnhancements] = useState<Record<number, number>>(() => {
    // Initialize from selectedBuffIds
    const initial: Record<number, number> = {}
    BASE_BUFF_IDS.forEach(baseId => {
      initial[baseId] = 0
    })
    selectedBuffIds.forEach(buffId => {
      const baseId = getBaseIdFromBuffId(buffId)
      const enhancement = getEnhancementFromBuffId(buffId)
      initial[baseId] = enhancement
    })
    return initial
  })

  const handleSelect = useCallback((buffId: number) => {
    const isDeselect = buffId < 0
    const actualBuffId = Math.abs(buffId)
    const baseId = getBaseIdFromBuffId(actualBuffId)
    const enhancement = getEnhancementFromBuffId(actualBuffId)

    // Update selection
    const newSelection = new Set(selectedBuffIds)

    // Remove any existing selection for this base buff
    BASE_BUFF_IDS.forEach(() => {
      const existingId = createBuffId(baseId, enhancements[baseId] as 0 | 1 | 2)
      newSelection.delete(existingId)
    })

    if (isDeselect) {
      // Just deselect - reset enhancement to 0
      setEnhancements(prev => ({ ...prev, [baseId]: 0 }))
    } else {
      // Add new selection
      newSelection.add(actualBuffId)
      setEnhancements(prev => ({ ...prev, [baseId]: enhancement }))
    }

    onSelectionChange(newSelection)
  }, [selectedBuffIds, enhancements, onSelectionChange])

  // Get base buffs for initial display, but show current enhancement level
  const baseBuffs = getBaseBuffs(buffs)

  // Create display buffs with current enhancement levels
  const displayBuffs = baseBuffs.map(baseBuff => {
    const enhancement = enhancements[baseBuff.baseId] || 0
    const displayBuffId = createBuffId(baseBuff.baseId, enhancement as 0 | 1 | 2)
    const displayBuff = buffs.find(b => Number(b.id) === displayBuffId)
    return displayBuff || baseBuff
  })

  return (
    <div className="space-y-2">
      <label className="text-sm font-medium">{t('pages.plannerMD.startBuffs')}</label>
      <div className="grid grid-cols-5 gap-3">
        {displayBuffs.map((buff) => {
          const currentBuffId = Number(buff.id)
          const isSelected = selectedBuffIds.has(currentBuffId)

          return (
            <StartBuffCard
              key={buff.baseId}
              buff={buff}
              allBuffs={buffs}
              i18n={i18n}
              battleKeywords={battleKeywords}
              isSelected={isSelected}
              onSelect={handleSelect}
            />
          )
        })}
      </div>
    </div>
  )
}
