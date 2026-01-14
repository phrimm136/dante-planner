import { useEffect, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useStartGiftPools } from '@/hooks/useStartGiftPools'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { useStartBuffData } from '@/hooks/useStartBuffData'
import type { MDVersion } from '@/lib/constants'
import { calculateMaxGiftSelection } from '@/lib/startGiftCalculator'
import { StartGiftRow } from './StartGiftRow'
import type { EGOGiftSpec, EGOGiftNameList } from '@/types/EGOGiftTypes'

interface StartGiftEditPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  mdVersion: MDVersion
  selectedBuffIds: Set<number>
  selectedKeyword: string | null
  selectedGiftIds: Set<string>
  onKeywordChange: (keyword: string | null) => void
  onGiftSelectionChange: (giftIds: Set<string>) => void
}

/**
 * Dialog for editing start gift selection
 * - Displays 10 keyword rows (vertical)
 * - Each row has keyword icon + 3 gift cards (horizontal)
 * - Single keyword selection at a time
 * - Gift selection gated by keyword selection
 * - Selection count = 1 + ADDITIONAL_START_EGO_GIFT_SELECT effects
 */
export function StartGiftEditPane({
  open,
  onOpenChange,
  mdVersion,
  selectedBuffIds,
  selectedKeyword,
  selectedGiftIds,
  onKeywordChange,
  onGiftSelectionChange,
}: StartGiftEditPaneProps) {
  const { t } = useTranslation(['planner', 'common'])

  // Load data
  const { data: pools } = useStartGiftPools(mdVersion)
  const { spec, i18n } = useEGOGiftListData()
  const { data: buffs } = useStartBuffData(mdVersion)

  // Calculate max selectable gifts
  const maxSelectable = useMemo(
    () => calculateMaxGiftSelection(buffs, selectedBuffIds),
    [buffs, selectedBuffIds]
  )

  // Trim excess gifts when EA changes
  useEffect(() => {
    if (selectedGiftIds.size > maxSelectable) {
      const newSelection = new Set<string>()
      let count = 0
      for (const id of selectedGiftIds) {
        if (count < maxSelectable) {
          newSelection.add(id)
          count++
        }
      }
      onGiftSelectionChange(newSelection)
    }
  }, [maxSelectable, selectedGiftIds, onGiftSelectionChange])

  // Row click (not gift) - just toggle row selection
  const handleRowSelect = (keyword: string) => {
    if (selectedKeyword === keyword) {
      onKeywordChange(null)
      onGiftSelectionChange(new Set())
    } else {
      onKeywordChange(keyword)
      onGiftSelectionChange(new Set())
    }
  }

  // Gift click - combined row + gift selection in ONE update
  const handleGiftClick = (rowKeyword: string, giftId: string) => {
    // Different row - select row AND gift together
    if (selectedKeyword !== rowKeyword) {
      onKeywordChange(rowKeyword)
      onGiftSelectionChange(new Set([giftId]))
      return
    }

    // Same row - toggle gift
    const newSelection = new Set(selectedGiftIds)
    if (newSelection.has(giftId)) {
      newSelection.delete(giftId)
    } else if (newSelection.size < maxSelectable) {
      newSelection.add(giftId)
    }
    onGiftSelectionChange(newSelection)
  }

  const keywords = Object.keys(pools)

  return (
    <>
      {/* Custom backdrop to block background interaction */}
      {open && (
        <div
          className="fixed inset-0 z-50 bg-black/50 animate-in fade-in-0"
          onClick={() => onOpenChange(false)}
        />
      )}

      <Dialog open={open} onOpenChange={onOpenChange} modal={false}>
        <DialogContent
          className="max-w-[95vw] lg:max-w-[1440px] duration-100"
          onPointerDownOutside={(e) => e.preventDefault()}
        >
        <DialogHeader>
          <DialogTitle>{t('pages.plannerMD.startEgoGift')}</DialogTitle>
        </DialogHeader>

        {/* EA Counter */}
        <div className="flex justify-end mb-4">
          <span className="text-sm text-muted-foreground">
            {t('pages.plannerMD.egoGiftSelection')}: {selectedGiftIds.size}/{maxSelectable}
          </span>
        </div>

        {/* 10 Keyword Rows */}
        <div className="space-y-2 max-h-[70vh] overflow-y-auto p-2">
          {keywords.map((keyword) => (
            <StartGiftRow
              key={keyword}
              keyword={keyword}
              giftIds={pools[keyword]}
              giftSpecMap={spec as Record<string, EGOGiftSpec>}
              giftNameMap={i18n as EGOGiftNameList}
              isRowSelected={selectedKeyword === keyword}
              selectedGiftIds={selectedGiftIds}
              maxSelectable={maxSelectable}
              onRowSelect={handleRowSelect}
              onGiftClick={handleGiftClick}
            />
          ))}
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => {
              onKeywordChange(null)
              onGiftSelectionChange(new Set())
            }}
          >
            {t('common:reset')}
          </Button>
          <Button onClick={() => { onOpenChange(false) }}>
            {t('common:done')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
    </>
  )
}
