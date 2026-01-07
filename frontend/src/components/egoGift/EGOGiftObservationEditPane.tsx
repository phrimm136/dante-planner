import { useState, useEffect, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useEGOGiftObservationData } from '@/hooks/useEGOGiftObservationData'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { SortMode } from '@/components/common/Sorter'
import { Sorter } from '@/components/common/Sorter'
import { StarlightCostDisplay } from '@/components/common/StarlightCostDisplay'
import { EGOGiftSelectionList } from './EGOGiftSelectionList'
import { EGOGiftObservationSelection } from './EGOGiftObservationSelection'
import { EGOGiftSearchBar } from './EGOGiftSearchBar'
import { EGOGiftKeywordFilter } from './EGOGiftKeywordFilter'
import { MAX_OBSERVABLE_GIFTS } from '@/lib/constants'

interface EGOGiftObservationEditPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  selectedGiftIds: Set<string>
  onSelectionChange: (giftIds: Set<string>) => void
}

/**
 * Dialog for editing EGO Gift Observation selection
 * Desktop: 9:1 grid (selection list left, selected gifts right)
 * Mobile: stacked layout
 * Local filter state resets on dialog close
 * Suspends while loading - wrap in Suspense boundary
 */
export function EGOGiftObservationEditPane({
  open,
  onOpenChange,
  selectedGiftIds,
  onSelectionChange,
}: EGOGiftObservationEditPaneProps) {
  const { t } = useTranslation(['planner', 'common'])

  // Load observation data (suspends while loading)
  const { data: observationData } = useEGOGiftObservationData()
  const { spec, i18n } = useEGOGiftListData()

  // Merge spec and i18n into EGOGiftListItem array
  const gifts = useMemo<EGOGiftListItem[]>(
    () =>
      Object.entries(spec).map(([id, specData]) => ({
        id,
        name: i18n[id] || id,
        tag: specData.tag as EGOGiftListItem['tag'],
        keyword: specData.keyword,
        attributeType: specData.attributeType,
        themePack: specData.themePack,
      })),
    [spec, i18n]
  )

  // LOCAL filter states (reset on close)
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState('')
  const [sortMode, setSortMode] = useState<SortMode>('tier-first')

  // Reset filters when dialog closes to provide clean slate for next edit session
  // Trade-off: Users lose filter state, but prevents confusion from invisible filters
  useEffect(() => {
    if (!open) {
      setSelectedKeywords(new Set())
      setSearchQuery('')
      setSortMode('tier-first')
    }
  }, [open])

  // Max selection enforced in handleGiftToggle - no useEffect needed

  const handleGiftToggle = (giftId: string) => {
    const newSelection = new Set(selectedGiftIds)
    if (newSelection.has(giftId)) {
      newSelection.delete(giftId)
    } else if (newSelection.size < MAX_OBSERVABLE_GIFTS) {
      newSelection.add(giftId)
    }
    onSelectionChange(newSelection)
  }

  // Calculate current cost from observation data
  const currentCost =
    observationData.observationEgoGiftCostDataList.find(
      (cost) => cost.egogiftCount === selectedGiftIds.size
    )?.starlightCost || 0

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[95vw] lg:max-w-[1440px] duration-100">
        <DialogHeader>
          <DialogTitle>{t('pages.plannerMD.egoGiftObservation')}</DialogTitle>
        </DialogHeader>

        {/* Cost display at top-right */}
        <div className="flex justify-end mb-4">
          <StarlightCostDisplay cost={currentCost} size="lg" />
        </div>

        {/* Filter row: keyword filter, sorter, search bar */}
        <div className="flex gap-4 justify-between flex-wrap">
          {/* Left side: Filters and Sorter */}
          <div className="flex gap-4 items-center flex-wrap">
            <EGOGiftKeywordFilter
              selectedKeywords={selectedKeywords}
              onSelectionChange={setSelectedKeywords}
            />
            <Sorter sortMode={sortMode} onSortModeChange={setSortMode} />
          </div>

          {/* Right side: Search bar */}
          <div className="shrink-0">
            <EGOGiftSearchBar searchQuery={searchQuery} onSearchChange={setSearchQuery} />
          </div>
        </div>

        {/* Main content: Desktop 9:1 grid, Mobile stacked */}
        <div className="grid grid-cols-1 lg:grid-cols-10 gap-4">
          {/* Left: Selection List (9 columns on desktop) */}
          <div className="lg:col-span-9">
            <h3 className="text-lg font-medium mb-2">{t('pages.plannerMD.selectEgoGifts')}</h3>
            <EGOGiftSelectionList
              gifts={gifts}
              giftIdFilter={observationData.observationEgoGiftDataList}
              selectedKeywords={selectedKeywords}
              searchQuery={searchQuery}
              sortMode={sortMode}
              selectedGiftIds={selectedGiftIds}
              maxSelectable={MAX_OBSERVABLE_GIFTS}
              onGiftSelect={handleGiftToggle}
            />
          </div>

          {/* Right: Selected Gifts (1 column on desktop) */}
          <div className="lg:col-span-1">
            <h3 className="text-lg font-medium mb-2">{t('pages.plannerMD.selectedEgoGifts')}</h3>
            <EGOGiftObservationSelection
              selectedGiftIds={Array.from(selectedGiftIds)}
              onGiftRemove={handleGiftToggle}
            />
          </div>
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => {
              onSelectionChange(new Set())
            }}
          >
            {t('common.reset')}
          </Button>
          <Button
            onClick={() => {
              onOpenChange(false)
            }}
          >
            {t('common.done')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
