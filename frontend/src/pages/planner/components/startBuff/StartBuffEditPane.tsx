import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { ENHANCEMENT_LABELS, type MDVersion } from '@/shared/gameData'
import { CARD_MOBILE_SCALE_DENSE } from '@/lib/constants'
import { START_BUFF_GEOMETRY } from '../../lib/cardLayout'
import { CardSlot, useSlotSizePx } from '@/shared/cardLayout'
import { START_BUFF_GRID_COLUMNS } from '../../lib/cardLayout'
import { getStartBuffEnhancementIconPath } from '@/shared/assets'
import { useStartBuffSelection } from '../../hooks/useStartBuffSelection'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import {
  BASE_BUFF_IDS,
  createBuffId,
  deriveEnhancements,
  getBaseIdFromBuffId,
} from '@/shared/gameText'
import type { EnhancementLevel } from '@/shared/gameText'
import { StarlightCostDisplay } from '../StarlightCostDisplay'
import { StartBuffCard } from './StartBuffCard'

interface StartBuffEditPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  mdVersion: MDVersion
}

/**
 * Dialog for editing start buff selection
 * Renders cards directly without intermediate Grid component
 */
export function StartBuffEditPane({ open, onOpenChange, mdVersion }: StartBuffEditPaneProps) {
  const { t } = useTranslation(['planner', 'common'])

  // Store state
  const selectedBuffIds = usePlannerEditorStore((s) => s.selectedBuffIds)
  const setSelectedBuffIds = usePlannerEditorStore((s) => s.setSelectedBuffIds)

  const { buffs, i18n, battleKeywords, displayBuffs, handleSelect } = useStartBuffSelection(
    mdVersion,
    selectedBuffIds,
    setSelectedBuffIds,
  )

  const mobileScale = CARD_MOBILE_SCALE_DENSE
  const { widthPx: columnWidth, heightPx: rowHeight } = useSlotSizePx(
    START_BUFF_GEOMETRY.size,
    mobileScale,
  )

  // Enhancement preview state for all cards (lifted from StartBuffCard)
  // Initialized from current selection; empty entries fall back to 0
  const [enhancementPreviews, setEnhancementPreviews] = useState<Record<number, EnhancementLevel>>(
    () => {
      return deriveEnhancements(selectedBuffIds)
    },
  )

  // Get enhancement for a given base buff
  const getEnhancement = (baseId: number): EnhancementLevel => {
    return enhancementPreviews[baseId] ?? 0
  }

  // Single card enhancement change (from card's +/++ buttons)
  const handleEnhancementChange = (baseId: number, level: EnhancementLevel) => {
    setEnhancementPreviews((prev) => ({ ...prev, [baseId]: level }))

    // If this buff is selected, update its ID in the selection
    const newSelection = new Set(selectedBuffIds)
    let wasSelected = false
    for (let l = 0; l <= 2; l++) {
      if (newSelection.delete(createBuffId(baseId, l as EnhancementLevel))) {
        wasSelected = true
      }
    }
    if (wasSelected) {
      newSelection.add(createBuffId(baseId, level))
      setSelectedBuffIds(newSelection)
    }
  }

  // Calculate total cost using preview enhancements for selected buffs
  const totalCost = (() => {
    let sum = 0
    for (const buffId of selectedBuffIds) {
      const buff = buffs.find((b) => Number(b.id) === buffId)
      if (buff) sum += buff.cost
    }
    return sum
  })()

  // Batch select all buffs at their current preview enhancement
  const handleSelectAll = () => {
    const newSelection = new Set<number>()
    for (const baseId of BASE_BUFF_IDS) {
      newSelection.add(createBuffId(baseId, getEnhancement(baseId)))
    }
    setSelectedBuffIds(newSelection)
  }

  // Batch set all enhancement previews (only updates selection for already-selected buffs)
  const handleBatchEnhancement = (level: EnhancementLevel) => {
    const newPreviews: Record<number, EnhancementLevel> = {}
    for (const baseId of BASE_BUFF_IDS) {
      newPreviews[baseId] = level
    }
    setEnhancementPreviews(newPreviews)

    // Update IDs for already-selected buffs to match new enhancement
    if (selectedBuffIds.size > 0) {
      const newSelection = new Set<number>()
      for (const buffId of selectedBuffIds) {
        const baseId = getBaseIdFromBuffId(buffId)
        newSelection.add(createBuffId(baseId, level))
      }
      setSelectedBuffIds(newSelection)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="max-w-[calc(100%-0.5rem)] sm:max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] flex flex-col"
        showCloseButton={false}
      >
        <DialogHeader className="shrink-0 border-b border-border pb-4">
          <div className="flex items-center gap-4 flex-wrap">
            <DialogTitle>{t('pages.plannerMD.startBuffs')}</DialogTitle>
            <div className="flex items-center gap-4 ml-auto">
              <StarlightCostDisplay cost={totalCost} size="lg" />
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setSelectedBuffIds(new Set())
                    setEnhancementPreviews({})
                  }}
                >
                  {t('common:reset')}
                </Button>
                <Button
                  size="sm"
                  onClick={() => {
                    onOpenChange(false)
                  }}
                >
                  {t('common:done')}
                </Button>
              </div>
            </div>
          </div>
        </DialogHeader>

        {/* Batch action row */}
        <div className="flex items-center gap-2 py-2 flex-wrap">
          <button
            type="button"
            className="selectable px-3 h-8 rounded-md border border-border text-sm"
            data-selected={selectedBuffIds.size === BASE_BUFF_IDS.length}
            onClick={handleSelectAll}
          >
            {t('common:selectAll')}
          </button>
          <button
            type="button"
            className="selectable px-3 h-8 rounded-md border border-border text-sm"
            onClick={() => {
              setSelectedBuffIds(new Set())
            }}
          >
            {t('common:unselectAll')}
          </button>

          <div className="w-px h-6 bg-border mx-1" />

          {([0, 1, 2] as const).map((level) => (
            <button
              key={level}
              type="button"
              className="selectable w-8 h-8 rounded-md border border-border flex items-center justify-center"
              onClick={() => {
                handleBatchEnhancement(level)
              }}
            >
              {level === 0 ? (
                <span className="text-xs font-bold">{ENHANCEMENT_LABELS[level]}</span>
              ) : (
                <img
                  src={getStartBuffEnhancementIconPath(level)}
                  alt={ENHANCEMENT_LABELS[level]}
                  className={level === 2 ? 'h-5' : 'h-4'}
                />
              )}
            </button>
          ))}
        </div>

        <div className="w-full flex-1 min-h-0 overflow-y-auto scrollbar-hide">
          <div
            className="bg-muted grid gap-2 w-max mx-auto"
            style={{
              gridTemplateColumns: `repeat(${String(START_BUFF_GRID_COLUMNS)}, ${String(columnWidth)}px)`,
              gridAutoRows: `${String(rowHeight)}px`,
            }}
          >
            {displayBuffs.map((buff) => {
              const enhancement = getEnhancement(buff.baseId)
              const buffId = createBuffId(buff.baseId, enhancement)
              const isSelected = selectedBuffIds.has(buffId)

              return (
                <CardSlot
                  key={buff.baseId}
                  size={START_BUFF_GEOMETRY.size}
                  mobileScale={mobileScale}
                >
                  <StartBuffCard
                    mdVersion={mdVersion}
                    buff={buff}
                    allBuffs={buffs}
                    i18n={i18n}
                    battleKeywords={battleKeywords}
                    isSelected={isSelected}
                    onSelect={handleSelect}
                    enhancement={enhancement}
                    onEnhancementChange={handleEnhancementChange}
                  />
                </CardSlot>
              )
            })}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
