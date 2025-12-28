import { useState, useEffect, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
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

interface EGOGiftObservationSectionProps {
  selectedGiftIds: Set<string>
  onGiftSelectionChange: (giftIds: Set<string>) => void
}

/**
 * EGO Gift Observation Section
 * Layout: Cost display (upper-right), Selection list (left), Selected gifts showroom (right)
 * Max 3 gifts can be selected, cost varies by count (70/160/270 starlight)
 * Suspends while loading - wrap in Suspense boundary
 */
export function EGOGiftObservationSection({
  selectedGiftIds,
  onGiftSelectionChange,
}: EGOGiftObservationSectionProps) {
  const { t } = useTranslation()

  // Load observation data (suspends while loading)
  const { data: observationData } = useEGOGiftObservationData()
  const { spec, i18n } = useEGOGiftListData()

  // Merge spec and i18n into EGOGiftListItem array
  const gifts = useMemo<EGOGiftListItem[]>(() =>
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

  // Filter states
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState('')
  const [sortMode, setSortMode] = useState<SortMode>('tier-first')

  // Enforce max selection limit
  useEffect(() => {
    if (selectedGiftIds.size > MAX_OBSERVABLE_GIFTS) {
      const newSelection = new Set<string>()
      let count = 0
      for (const id of selectedGiftIds) {
        if (count < MAX_OBSERVABLE_GIFTS) {
          newSelection.add(id)
          count++
        }
      }
      onGiftSelectionChange(newSelection)
    }
  }, [selectedGiftIds, onGiftSelectionChange])

  const handleGiftToggle = (giftId: string) => {
    const newSelection = new Set(selectedGiftIds)
    if (newSelection.has(giftId)) {
      newSelection.delete(giftId)
    } else if (newSelection.size < MAX_OBSERVABLE_GIFTS) {
      newSelection.add(giftId)
    }
    onGiftSelectionChange(newSelection)
  }

  // Calculate current cost
  const currentCost = observationData.observationEgoGiftCostDataList.find(
    (cost) => cost.egogiftCount === selectedGiftIds.size
  )?.starlightCost || 0

  return (
    <div className="space-y-4">
      {/* Header with Cost Display */}
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold">
          {t('pages.plannerMD.egoGiftObservation')}
        </h2>
        <StarlightCostDisplay cost={currentCost} size="lg" />
      </div>
      {/* Top row: Keyword filter and sorter on left, Search bar on right */}
      <div className="flex gap-4 justify-between">
        {/* Left side: Filters and Sorter */}
        <div className="flex gap-4 items-center">
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

      {/* Bottom: EGO Gift list */}
      <div className="grid grid-cols-10 gap-4">
        {/* Left: Selection List (9 columns) */}
        <div className="col-span-9">
          <h3 className="text-lg font-medium mb-2">
            {t('pages.plannerMD.selectGifts')}
          </h3>
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

        {/* Right: Selected Gifts Showroom (1 column) */}
        <div className="col-span-1">
          <h3 className="text-lg font-medium mb-2">
            {t('pages.plannerMD.selectedGifts')}
          </h3>
          <EGOGiftObservationSelection
            selectedGiftIds={Array.from(selectedGiftIds)}
            onGiftRemove={handleGiftToggle}
          />
        </div>
      </div>
    </div>
  )
}
