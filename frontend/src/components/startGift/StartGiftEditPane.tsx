import { useEffect, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useStartGiftPools } from '@/hooks/useStartGiftPools'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { useStartBuffData } from '@/hooks/useStartBuffData'
import { usePlannerEditorStore } from '@/stores/usePlannerEditorStore'
import type { MDVersion } from '@/lib/constants'
import { calculateMaxGiftSelection } from '@/lib/startGiftCalculator'
import { StartGiftRow } from './StartGiftRow'
import type { EGOGiftSpec, EGOGiftNameList } from '@/types/EGOGiftTypes'

interface StartGiftEditPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  mdVersion: MDVersion
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
}: StartGiftEditPaneProps) {
  // Store state
  const selectedBuffIds = usePlannerEditorStore((s) => s.selectedBuffIds)
  const selectedKeyword = usePlannerEditorStore((s) => s.selectedGiftKeyword)
  const selectedGiftIds = usePlannerEditorStore((s) => s.selectedGiftIds)
  const setSelectedKeyword = usePlannerEditorStore((s) => s.setSelectedGiftKeyword)
  const setSelectedGiftIds = usePlannerEditorStore((s) => s.setSelectedGiftIds)
  const comprehensiveGiftIds = usePlannerEditorStore((s) => s.comprehensiveGiftIds)
  const setComprehensiveGiftIds = usePlannerEditorStore((s) => s.setComprehensiveGiftIds)
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
      const trimmedIds: string[] = []
      let count = 0
      for (const id of selectedGiftIds) {
        if (count < maxSelectable) {
          newSelection.add(id)
          count++
        } else {
          trimmedIds.push(id)
        }
      }
      if (trimmedIds.length > 0) {
        const newComprehensive = new Set(comprehensiveGiftIds)
        for (const id of trimmedIds) {
          newComprehensive.delete(id)
        }
        setComprehensiveGiftIds(newComprehensive)
      }
      setSelectedGiftIds(newSelection)
    }
  }, [maxSelectable, selectedGiftIds, setSelectedGiftIds, comprehensiveGiftIds, setComprehensiveGiftIds])

  // Row click (not gift) - just toggle row selection
  const handleRowSelect = (keyword: string) => {
    // Remove old gifts from comprehensive before clearing
    if (selectedGiftIds.size > 0) {
      const newComprehensive = new Set(comprehensiveGiftIds)
      for (const id of selectedGiftIds) {
        newComprehensive.delete(id)
      }
      setComprehensiveGiftIds(newComprehensive)
    }

    if (selectedKeyword === keyword) {
      setSelectedKeyword(null)
      setSelectedGiftIds(new Set())
    } else {
      setSelectedKeyword(keyword)
      setSelectedGiftIds(new Set())
    }
  }

  // Gift click - combined row + gift selection in ONE update
  const handleGiftClick = (rowKeyword: string, giftId: string) => {
    const newComprehensive = new Set(comprehensiveGiftIds)

    // Different row - select row AND gift together
    if (selectedKeyword !== rowKeyword) {
      // Remove old row's gifts from comprehensive
      for (const id of selectedGiftIds) {
        newComprehensive.delete(id)
      }
      // Add new gift to comprehensive
      newComprehensive.add(giftId)
      setComprehensiveGiftIds(newComprehensive)
      setSelectedKeyword(rowKeyword)
      setSelectedGiftIds(new Set([giftId]))
      return
    }

    // Same row - toggle gift
    const newSelection = new Set(selectedGiftIds)
    if (newSelection.has(giftId)) {
      newSelection.delete(giftId)
      newComprehensive.delete(giftId)
    } else if (newSelection.size < maxSelectable) {
      newSelection.add(giftId)
      newComprehensive.add(giftId)
    }
    setComprehensiveGiftIds(newComprehensive)
    setSelectedGiftIds(newSelection)
  }

  const keywords = Object.keys(pools)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="max-w-[calc(100%-0.5rem)] sm:max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] flex flex-col"
        showCloseButton={false}
      >
        <DialogHeader className="shrink-0 border-b border-border pb-4">
          <div className="flex items-center gap-4 flex-wrap">
            <DialogTitle>{t('pages.plannerMD.startEgoGift')}</DialogTitle>
            <div className="flex items-center gap-4 ml-auto">
              {/* EA Counter */}
              <span className="text-sm text-muted-foreground">
                {t('pages.plannerMD.egoGiftSelection')}: {selectedGiftIds.size}/{maxSelectable}
              </span>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    if (selectedGiftIds.size > 0) {
                      const newComprehensive = new Set(comprehensiveGiftIds)
                      for (const id of selectedGiftIds) {
                        newComprehensive.delete(id)
                      }
                      setComprehensiveGiftIds(newComprehensive)
                    }
                    setSelectedKeyword(null)
                    setSelectedGiftIds(new Set())
                  }}
                >
                  {t('common:reset')}
                </Button>
                <Button size="sm" onClick={() => { onOpenChange(false) }}>
                  {t('common:done')}
                </Button>
              </div>
            </div>
          </div>
        </DialogHeader>

        {/* Scrollable content area with visual margin */}
        <div className="flex-1 overflow-y-auto py-4 -mx-6 px-6">

          {/* 10 Keyword Rows */}
          <div className="space-y-2">
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
        </div>
      </DialogContent>
    </Dialog>
  )
}
