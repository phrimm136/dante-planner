import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { SortMode } from '@/components/common/Sorter'
import type { EnhancementLevel } from '@/lib/constants'
import { Sorter } from '@/components/common/Sorter'
import { PlannerSection } from '@/components/common/PlannerSection'
import { EGOGiftSelectionList } from './EGOGiftSelectionList'
import { EGOGiftSearchBar } from './EGOGiftSearchBar'
import { EGOGiftKeywordFilter } from './EGOGiftKeywordFilter'
import {
  encodeGiftSelection,
  decodeGiftSelection,
  findEncodedGiftId,
  getCascadeIngredients,
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
      recipe: specData.recipe,
    })),
    [spec, i18n]
  )

  // Build O(1) lookup map for recipe cascade selection
  const specById = useMemo(() => new Map(Object.entries(spec)), [spec])

  // Filter states
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState('')
  const [sortMode, setSortMode] = useState<SortMode>('tier-first')

  /**
   * Handle enhancement selection with toggle logic and cascade:
   * - No selection + click level → select gift with that level + cascade ingredients
   * - Selected + click same level → deselect gift (no reverse cascade)
   * - Selected + click different level → change enhancement level
   */
  const handleEnhancementSelect = (giftId: string, enhancement: EnhancementLevel) => {
    const newSelection = new Set(selectedGiftIds)
    const existingEncodedId = findEncodedGiftId(giftId, selectedGiftIds)

    if (existingEncodedId) {
      // Gift is already selected
      const { enhancement: currentEnhancement } = decodeGiftSelection(existingEncodedId)

      if (currentEnhancement === enhancement) {
        // Same level clicked - deselect (no reverse cascade)
        newSelection.delete(existingEncodedId)
      } else {
        // Different level clicked - update enhancement
        newSelection.delete(existingEncodedId)
        newSelection.add(encodeGiftSelection(enhancement, giftId))
      }
    } else {
      // Gift not selected - add with new enhancement
      newSelection.add(encodeGiftSelection(enhancement, giftId))

      // Cascade-select recipe ingredients (if any)
      const giftSpec = specById.get(giftId)
      const ingredientIds = getCascadeIngredients(giftSpec?.recipe)
      for (const ingredientId of ingredientIds) {
        const ingredientIdStr = String(ingredientId)
        // Only add if not already selected (avoid overwriting user's enhancement choice)
        if (!findEncodedGiftId(ingredientIdStr, newSelection)) {
          newSelection.add(encodeGiftSelection(0, ingredientIdStr))
        }
      }
    }

    onGiftSelectionChange(newSelection)
  }

  return (
    <PlannerSection title={t('pages.plannerMD.comprehensiveGiftList')}>
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
    </PlannerSection>
  )
}
