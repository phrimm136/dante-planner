import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { SortMode } from '@/components/common/Sorter'
import type { EnhancementLevel } from '@/lib/constants'
import { Sorter } from '@/components/common/Sorter'
import { EGOGiftSelectionList } from './EGOGiftSelectionList'
import { EGOGiftSearchBar } from './EGOGiftSearchBar'
import { EGOGiftKeywordFilter } from './EGOGiftKeywordFilter'
import {
  encodeGiftSelection,
  decodeGiftSelection,
  findEncodedGiftId,
} from '@/lib/egoGiftEncoding'

interface EGOGiftComprehensiveListSectionProps {
  /** Set of encoded gift IDs (enhancement + giftId format) */
  selectedGiftIds: Set<string>
  onGiftSelectionChange: (giftIds: Set<string>) => void
}

/**
 * EGO Gift Comprehensive List Section
 * Displays ALL gifts (no pool filtering) with enhancement selection
 * Single unified list showing selected/unselected state directly on cards
 * Suspends while loading - wrap in Suspense boundary
 */
export function EGOGiftComprehensiveListSection({
  selectedGiftIds,
  onGiftSelectionChange,
}: EGOGiftComprehensiveListSectionProps) {
  const { t } = useTranslation()

  // Load gift data (suspends while loading)
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

  /**
   * Handle enhancement selection with toggle logic:
   * - No selection + click level → select gift with that level
   * - Selected + click same level → deselect gift
   * - Selected + click different level → change enhancement level
   */
  const handleEnhancementSelect = (giftId: string, enhancement: EnhancementLevel) => {
    const newSelection = new Set(selectedGiftIds)
    const existingEncodedId = findEncodedGiftId(giftId, selectedGiftIds)

    if (existingEncodedId) {
      // Gift is already selected
      const { enhancement: currentEnhancement } = decodeGiftSelection(existingEncodedId)

      if (currentEnhancement === enhancement) {
        // Same level clicked - deselect
        newSelection.delete(existingEncodedId)
      } else {
        // Different level clicked - update enhancement
        newSelection.delete(existingEncodedId)
        newSelection.add(encodeGiftSelection(enhancement, giftId))
      }
    } else {
      // Gift not selected - add with new enhancement
      newSelection.add(encodeGiftSelection(enhancement, giftId))
    }

    onGiftSelectionChange(newSelection)
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <h2 className="text-xl font-semibold">
        {t('pages.plannerMD.comprehensiveGiftList')}
      </h2>

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

      {/* Full-width EGO Gift list with enhancement selection */}
      <EGOGiftSelectionList
        gifts={gifts}
        selectedKeywords={selectedKeywords}
        searchQuery={searchQuery}
        sortMode={sortMode}
        selectedGiftIds={selectedGiftIds}
        maxSelectable={Infinity}
        enableEnhancementSelection={true}
        onEnhancementSelect={handleEnhancementSelect}
      />
    </div>
  )
}
