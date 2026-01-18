import { useState, useEffect, useMemo, startTransition, useRef, useCallback } from 'react'
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
import { usePlannerEditorStore } from '@/stores/usePlannerEditorStore'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { SortMode } from '@/components/common/Sorter'
import { Sorter } from '@/components/common/Sorter'
import { SearchBar } from '@/components/common/SearchBar'
import { StarlightCostDisplay } from '@/components/common/StarlightCostDisplay'
import { sortEGOGifts } from '@/lib/egoGiftSort'
import { EGOGiftSelectionList } from './EGOGiftSelectionList'
import { EGOGiftObservationSelection } from './EGOGiftObservationSelection'
import { EGOGiftKeywordFilter } from './EGOGiftKeywordFilter'
import { MAX_OBSERVABLE_GIFTS } from '@/lib/constants'

interface EGOGiftObservationEditPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
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
}: EGOGiftObservationEditPaneProps) {
  // Store state
  const selectedGiftIds = usePlannerEditorStore((s) => s.observationGiftIds)
  const setObservationGiftIds = usePlannerEditorStore((s) => s.setObservationGiftIds)
  const { t } = useTranslation(['planner', 'common'])

  // Load observation data (suspends while loading)
  const { data: observationData } = useEGOGiftObservationData()
  const { spec, i18n } = useEGOGiftListData()

  // LOCAL filter states
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState('')
  const [sortMode, setSortMode] = useState<SortMode>('tier-first')

  // Merge spec and i18n into EGOGiftListItem array
  const gifts = useMemo<EGOGiftListItem[]>(() => {
    return Object.entries(spec).map(([id, specData]) => ({
      id,
      name: i18n[id] || id,
      tag: specData.tag as EGOGiftListItem['tag'],
      keyword: specData.keyword,
      attributeType: specData.attributeType,
      themePack: specData.themePack,
      maxEnhancement: specData.maxEnhancement,
    }))
  }, [spec, i18n])

  // Sort gifts (apply giftIdFilter + sort)
  const sortedGifts = useMemo(() => {
    let filtered = gifts
    // Apply ID filter (observation eligible gifts)
    if (observationData.observationEgoGiftDataList.length > 0) {
      const idSet = new Set(observationData.observationEgoGiftDataList.map(String))
      filtered = filtered.filter((gift) => idSet.has(gift.id))
    }
    return sortEGOGifts(filtered, sortMode)
  }, [gifts, observationData.observationEgoGiftDataList, sortMode])

  // Reset filters when dialog closes
  useEffect(() => {
    if (!open) {
      setSelectedKeywords(new Set())
      setSearchQuery('')
      setSortMode('tier-first')
    }
  }, [open])

  // Use ref to always access latest selectedGiftIds in stable callback
  const selectedGiftIdsRef = useRef(selectedGiftIds)
  selectedGiftIdsRef.current = selectedGiftIds

  // Stable callback - uses ref to get latest state
  const handleGiftToggle = useCallback((giftId: string) => {
    startTransition(() => {
      const current = selectedGiftIdsRef.current
      const newSelection = new Set(current)
      if (newSelection.has(giftId)) {
        newSelection.delete(giftId)
      } else if (newSelection.size < MAX_OBSERVABLE_GIFTS) {
        newSelection.add(giftId)
      }
      setObservationGiftIds(newSelection)
    })
  }, [setObservationGiftIds])

  // Calculate current cost from observation data
  const currentCost =
    observationData.observationEgoGiftCostDataList.find(
      (cost) => cost.egogiftCount === selectedGiftIds.size
    )?.starlightCost || 0

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[30em] lg:max-w-[1440px]">
          <DialogHeader>
            <DialogTitle>{t('pages.plannerMD.egoGiftObservation')}</DialogTitle>
          </DialogHeader>

          {/* Cost display at top-right */}
          <div className="flex justify-end mb-4">
            <StarlightCostDisplay cost={currentCost} size="lg" />
          </div>

          {/* Filter row: keyword filter, sorter, search bar */}
          <div className="flex flex-col sm:flex-row gap-4 sm:justify-between">
            {/* Left side: Filters and Sorter */}
            <div className="flex flex-col sm:flex-row gap-4 sm:items-center min-w-0">
              <div className="min-w-0">
                <EGOGiftKeywordFilter
                  selectedKeywords={selectedKeywords}
                  onSelectionChange={setSelectedKeywords}
                />
              </div>
              <Sorter sortMode={sortMode} onSortModeChange={setSortMode} />
            </div>

            {/* Right side: Search bar */}
            <div className="min-w-0 sm:shrink-0">
              <SearchBar
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                placeholder={t('deckBuilder.egoGiftSearchPlaceholder')}
              />
            </div>
          </div>

          {/* Main content: Desktop 9:1 grid, Mobile stacked */}
          <div className="grid grid-cols-1 lg:grid-cols-10 gap-4">
            {/* Left: Selection List (9 columns on desktop) */}
            <div className="lg:col-span-9">
              <EGOGiftSelectionList
                gifts={sortedGifts}
                selectedKeywords={selectedKeywords}
                searchQuery={searchQuery}
                selectedGiftIds={selectedGiftIds}
                maxSelectable={MAX_OBSERVABLE_GIFTS}
                onGiftSelect={handleGiftToggle}
              />
            </div>

            {/* Right: Selected Gifts (1 column on desktop) */}
            <div className="lg:col-span-1 w-32">
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
                setObservationGiftIds(new Set())
              }}
            >
              {t('common:reset')}
            </Button>
            <Button
              onClick={() => {
                onOpenChange(false)
              }}
            >
              {t('common:done')}
            </Button>
          </DialogFooter>
        </DialogContent>
    </Dialog>
  )
}
