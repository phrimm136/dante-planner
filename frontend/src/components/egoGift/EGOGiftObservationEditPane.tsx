import { useState, useEffect, useMemo, startTransition, useRef, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useEGOGiftObservationData } from '@/hooks/useEGOGiftObservationData'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { usePlannerEditorStore } from '@/stores/usePlannerEditorStore'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { SortMode } from '@/components/common/Sorter'
import { EGOGiftFilterBar } from './EGOGiftFilterBar'
import { StarlightCostDisplay } from '@/components/common/StarlightCostDisplay'
import { sortEGOGifts } from '@/lib/egoGiftSort'
import { EGOGiftSelectionList } from './EGOGiftSelectionList'
import { EGOGiftObservationSelection } from './EGOGiftObservationSelection'
import { MAX_OBSERVABLE_GIFTS } from '@/lib/constants'

interface EGOGiftObservationEditPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  mdVersion: number
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
  mdVersion,
}: EGOGiftObservationEditPaneProps) {
  // Store state
  const selectedGiftIds = usePlannerEditorStore((s) => s.observationGiftIds)
  const setObservationGiftIds = usePlannerEditorStore((s) => s.setObservationGiftIds)
  const comprehensiveGiftIds = usePlannerEditorStore((s) => s.comprehensiveGiftIds)
  const setComprehensiveGiftIds = usePlannerEditorStore((s) => s.setComprehensiveGiftIds)
  const { t } = useTranslation(['planner', 'common'])

  // Load observation data (suspends while loading)
  const { data: observationData } = useEGOGiftObservationData(mdVersion)
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

  // Use ref to always access latest state in stable callback
  const selectedGiftIdsRef = useRef(selectedGiftIds)
  selectedGiftIdsRef.current = selectedGiftIds
  const comprehensiveGiftIdsRef = useRef(comprehensiveGiftIds)
  comprehensiveGiftIdsRef.current = comprehensiveGiftIds

  // Stable callback - uses ref to get latest state
  const handleGiftToggle = useCallback((giftId: string) => {
    startTransition(() => {
      const current = selectedGiftIdsRef.current
      const currentComprehensive = comprehensiveGiftIdsRef.current
      const newSelection = new Set(current)
      const newComprehensive = new Set(currentComprehensive)

      if (newSelection.has(giftId)) {
        newSelection.delete(giftId)
        newComprehensive.delete(giftId)
      } else if (newSelection.size < MAX_OBSERVABLE_GIFTS) {
        newSelection.add(giftId)
        newComprehensive.add(giftId)
      }

      setObservationGiftIds(newSelection)
      setComprehensiveGiftIds(newComprehensive)
    })
  }, [setObservationGiftIds, setComprehensiveGiftIds])

  // Calculate current cost from observation data
  const currentCost =
    observationData.observationEgoGiftCostDataList.find(
      (cost) => cost.egogiftCount === selectedGiftIds.size
    )?.starlightCost || 0

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[calc(100%-0.5rem)] sm:max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] flex flex-col" showCloseButton={false}>
          <DialogHeader className="shrink-0 border-b border-border pb-4">
            <div className="flex items-center gap-4 flex-wrap">
              <DialogTitle>{t('pages.plannerMD.egoGiftObservation')}</DialogTitle>
              <div className="flex items-center gap-4 ml-auto">
                <StarlightCostDisplay cost={currentCost} size="lg" />
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
                      setObservationGiftIds(new Set())
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

          {/* Scrollable content area */}
          <div className="flex-1 overflow-y-auto py-4 -mx-6 px-6 flex flex-col gap-4">
            <EGOGiftFilterBar
              selectedKeywords={selectedKeywords}
              onKeywordsChange={setSelectedKeywords}
              sortMode={sortMode}
              onSortModeChange={setSortMode}
              searchQuery={searchQuery}
              onSearchChange={setSearchQuery}
            />

            {/* Main content: Portrait phones stacked, ≥640px side-by-side */}
            <div className="flex flex-col sm:flex-row gap-2">
              {/* Selection List - takes remaining space */}
              <div className="flex-1 min-w-0">
                <EGOGiftSelectionList
                  gifts={sortedGifts}
                  selectedKeywords={selectedKeywords}
                  searchQuery={searchQuery}
                  selectedGiftIds={selectedGiftIds}
                  maxSelectable={MAX_OBSERVABLE_GIFTS}
                  onGiftSelect={handleGiftToggle}
                />
              </div>

              {/* Selected Gifts - w-24 for tablets, w-32 for desktop */}
              <div className="sm:w-24 lg:w-32 sm:shrink-0 lg:shrink-0">
                <EGOGiftObservationSelection
                  selectedGiftIds={Array.from(selectedGiftIds)}
                  onGiftRemove={handleGiftToggle}
                />
              </div>
            </div>
          </div>
        </DialogContent>
    </Dialog>
  )
}
