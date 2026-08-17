import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { useEGOGiftObservationData } from '@/pages/egoGift'
import { useEGOGiftListData } from '@/pages/egoGift'
import { useCappedSelection } from '../../hooks/useCappedSelection'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import type { EGOGiftListItem } from '@/pages/egoGift'
import type { SortMode } from '@/shared/filter'
import { EGOGiftFilterBar } from '@/pages/egoGift'
import { SelectorPaneShell } from '../SelectorPaneShell'
import { StarlightCostDisplay } from '../StarlightCostDisplay'
import { sortEGOGifts } from '@/pages/egoGift'
import { EGOGiftSelectionList } from '@/pages/egoGift'
import { EGOGiftObservationSelection } from '@/pages/egoGift'
import { MAX_OBSERVABLE_GIFTS } from '@/shared/gameData'
import { toGiftListItems } from '@/pages/egoGift'

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
  const gifts: EGOGiftListItem[] = (() => {
    return toGiftListItems(spec, i18n)
  })()

  // Sort gifts (apply giftIdFilter + sort)
  const sortedGifts = (() => {
    let filtered = gifts
    // Apply ID filter (observation eligible gifts)
    if (observationData.observationEgoGiftDataList.length > 0) {
      const idSet = new Set(observationData.observationEgoGiftDataList.map(String))
      filtered = filtered.filter((gift) => idSet.has(gift.id))
    }
    return sortEGOGifts(filtered, sortMode)
  })()

  // Reset filters when dialog closes
  useEffect(() => {
    if (!open) {
      setSelectedKeywords(new Set())
      setSearchQuery('')
      setSortMode('tier-first')
    }
  }, [open])

  const { toggle: handleGiftToggle, clear } = useCappedSelection({
    cap: MAX_OBSERVABLE_GIFTS,
    selected: selectedGiftIds,
    onSelectedChange: setObservationGiftIds,
    mirror: comprehensiveGiftIds,
    onMirrorChange: setComprehensiveGiftIds,
  })

  // Calculate current cost from observation data
  const currentCost =
    observationData.observationEgoGiftCostDataList.find(
      (cost) => cost.egogiftCount === selectedGiftIds.size,
    )?.starlightCost || 0

  return (
    <SelectorPaneShell
      open={open}
      onOpenChange={onOpenChange}
      title={t('pages.plannerMD.egoGiftObservation')}
      headerActions={
        <>
          <StarlightCostDisplay cost={currentCost} size="lg" />
          <Button variant="outline" size="sm" onClick={clear}>
            {t('common:reset')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
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
    </SelectorPaneShell>
  )
}
