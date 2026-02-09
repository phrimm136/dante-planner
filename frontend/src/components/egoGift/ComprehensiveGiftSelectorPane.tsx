import { useState, useMemo, useEffect, startTransition, useRef, useCallback } from 'react'
import { useTranslation } from 'react-i18next'

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import {
  encodeGiftSelection,
  decodeGiftSelection,
  findEncodedGiftId,
  getCascadeIngredients,
} from '@/lib/egoGiftEncoding'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EnhancementLevel } from '@/lib/constants'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { usePlannerEditorStore } from '@/stores/usePlannerEditorStore'
import { sortEGOGifts } from '@/lib/egoGiftSort'
import { SearchBar } from '@/components/common/SearchBar'
import { EGOGiftSelectionList } from '@/components/egoGift/EGOGiftSelectionList'
import { EGOGiftKeywordFilter } from '@/components/egoGift/EGOGiftKeywordFilter'
import { Sorter, type SortMode } from '@/components/common/Sorter'

interface ComprehensiveGiftSelectorPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

/**
 * Dialog for selecting EGO gifts with cascade selection logic.
 * Shows ALL gifts (no theme pack restriction, but applies keyword/search filters).
 * Supports enhancement selection (0-3 levels) with automatic cascade for recipes.
 * State is managed by store.
 */
export function ComprehensiveGiftSelectorPane({
  open,
  onOpenChange,
}: ComprehensiveGiftSelectorPaneProps) {
  // Store state
  const selectedGiftIds = usePlannerEditorStore((s) => s.comprehensiveGiftIds)
  const setComprehensiveGiftIds = usePlannerEditorStore((s) => s.setComprehensiveGiftIds)
  const { t } = useTranslation(['planner', 'common'])
  const { spec, i18n } = useEGOGiftListData()

  // Filter states (local to pane UI - reset on reopen)
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState('')
  const [sortMode, setSortMode] = useState<SortMode>('tier-first')

  // Reset filters when dialog closes
  useEffect(() => {
    if (!open) {
      setSelectedKeywords(new Set())
      setSearchQuery('')
      setSortMode('tier-first')
    }
  }, [open])

  // Convert to EGOGiftListItem array
  const gifts = useMemo<EGOGiftListItem[]>(() => {
    return Object.entries(spec).map(([id, specData]) => ({
      id,
      name: i18n[id] || id,
      tag: specData.tag as EGOGiftListItem['tag'],
      keyword: specData.keyword,
      attributeType: specData.attributeType,
      themePack: specData.themePack,
      maxEnhancement: specData.maxEnhancement,
      recipe: specData.recipe,
    }))
  }, [spec, i18n])

  // Build O(1) lookup map for recipe cascade selection
  const specById = useMemo(() => {
    return new Map(Object.entries(spec))
  }, [spec])

  // Sort gifts (no ID filter for comprehensive list)
  const sortedGifts = useMemo(() => {
    return sortEGOGifts(gifts, sortMode)
  }, [gifts, sortMode])

  // Use ref to always access latest selectedGiftIds in stable callback
  const selectedGiftIdsRef = useRef(selectedGiftIds)
  selectedGiftIdsRef.current = selectedGiftIds

  /**
   * Handle enhancement selection with toggle logic and cascade
   */
  const handleEnhancementSelect = useCallback((
    giftId: string,
    enhancement: EnhancementLevel
  ) => {
    startTransition(() => {
      const current = selectedGiftIdsRef.current
      const newSelection = new Set(current)
      const existingEncodedId = findEncodedGiftId(giftId, current)

      if (existingEncodedId) {
        const { enhancement: currentEnhancement } = decodeGiftSelection(existingEncodedId)

        if (currentEnhancement === enhancement) {
          newSelection.delete(existingEncodedId)
        } else {
          newSelection.delete(existingEncodedId)
          newSelection.add(encodeGiftSelection(enhancement, giftId))
        }
      } else {
        newSelection.add(encodeGiftSelection(enhancement, giftId))

        const giftSpec = specById.get(giftId)
        if (!giftSpec) {
          setComprehensiveGiftIds(newSelection)
          return
        }

        const ingredientIds = getCascadeIngredients(giftSpec.recipe)
        const visited = new Set<string>([giftId])

        for (const ingredientId of ingredientIds) {
          const ingredientIdStr = String(ingredientId)
          if (visited.has(ingredientIdStr)) continue
          visited.add(ingredientIdStr)
          if (!findEncodedGiftId(ingredientIdStr, newSelection)) {
            newSelection.add(encodeGiftSelection(0, ingredientIdStr))
          }
        }
      }

      setComprehensiveGiftIds(newSelection)
    })
  }, [setComprehensiveGiftIds, specById])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] overflow-hidden flex flex-col" showCloseButton={false}>
        <DialogHeader>
          <div className="flex items-center justify-between">
            <DialogTitle>
              {t('pages.plannerMD.comprehensiveEgoGiftList')}
            </DialogTitle>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => { setComprehensiveGiftIds(new Set()) }}
              >
                {t('common:reset')}
              </Button>
              <Button size="sm" onClick={() => { onOpenChange(false) }}>
                {t('common:done')}
              </Button>
            </div>
          </div>
        </DialogHeader>

        {/* Filter bar */}
        <div className="space-y-2 py-2">
          {/* Row 1: Keyword filter (full width) */}
          <div>
            <EGOGiftKeywordFilter
              selectedKeywords={selectedKeywords}
              onSelectionChange={setSelectedKeywords}
            />
          </div>
          {/* Row 2: Sorter + SearchBar */}
          <div className="flex flex-col sm:flex-row gap-2 sm:gap-4">
            <div className="shrink-0">
              <Sorter sortMode={sortMode} onSortModeChange={setSortMode} />
            </div>
            <div className="flex-1 min-w-0">
              <SearchBar
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                placeholder={t('deckBuilder.egoGiftSearchPlaceholder')}
              />
            </div>
          </div>
        </div>

        {/* Gift selection list */}
        <div className="flex-1 overflow-hidden">
          <EGOGiftSelectionList
            gifts={sortedGifts}
            selectedKeywords={selectedKeywords}
            searchQuery={searchQuery}
            selectedGiftIds={selectedGiftIds}
            maxSelectable={Infinity}
            enableEnhancementSelection
            onEnhancementSelect={handleEnhancementSelect}
          />
        </div>
      </DialogContent>
    </Dialog>
  )
}
