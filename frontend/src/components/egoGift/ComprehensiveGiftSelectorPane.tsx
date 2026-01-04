import { useState, useMemo, useEffect } from 'react'
import { useTranslation } from 'react-i18next'

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
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
import { EGOGiftSelectionList } from '@/components/egoGift/EGOGiftSelectionList'
import { EGOGiftSearchBar } from '@/components/egoGift/EGOGiftSearchBar'
import { EGOGiftKeywordFilter } from '@/components/egoGift/EGOGiftKeywordFilter'
import { Sorter, type SortMode } from '@/components/common/Sorter'

interface ComprehensiveGiftSelectorPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  selectedGiftIds: Set<string>
  onGiftSelectionChange: (giftIds: Set<string>) => void
}

/**
 * Dialog for selecting EGO gifts with cascade selection logic.
 * Shows ALL gifts (no theme pack restriction, but applies keyword/search filters).
 * Supports enhancement selection (0-3 levels) with automatic cascade for recipes.
 * State is managed by parent; this pane calls onGiftSelectionChange.
 */
export function ComprehensiveGiftSelectorPane({
  open,
  onOpenChange,
  selectedGiftIds,
  onGiftSelectionChange,
}: ComprehensiveGiftSelectorPaneProps) {
  const { t } = useTranslation()
  const { spec, i18n } = useEGOGiftListData()

  // Filter states (local to pane UI - reset on reopen)
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(
    new Set()
  )
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

  // Convert to EGOGiftListItem array
  const gifts: EGOGiftListItem[] = Object.entries(spec).map(
    ([id, specData]) => ({
      id,
      name: i18n[id] || id,
      tag: specData.tag as EGOGiftListItem['tag'],
      keyword: specData.keyword,
      attributeType: specData.attributeType,
      themePack: specData.themePack,
      recipe: specData.recipe,
    })
  )

  // Build O(1) lookup map for recipe cascade selection
  const specById = useMemo(() => new Map(Object.entries(spec)), [spec])

  /**
   * Handle enhancement selection with toggle logic and cascade:
   * - No selection + click level -> select gift with that level + cascade ingredients
   * - Selected + click same level -> deselect gift (no reverse cascade)
   * - Selected + click different level -> change enhancement level
   */
  const handleEnhancementSelect = (
    giftId: string,
    enhancement: EnhancementLevel
  ) => {
    const newSelection = new Set(selectedGiftIds)
    const existingEncodedId = findEncodedGiftId(giftId, selectedGiftIds)

    if (existingEncodedId) {
      // Gift is already selected
      const { enhancement: currentEnhancement } =
        decodeGiftSelection(existingEncodedId)

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
      if (!giftSpec) {
        // Gift spec not found - skip cascade but allow selection
        onGiftSelectionChange(newSelection)
        return
      }

      const ingredientIds = getCascadeIngredients(giftSpec.recipe)
      // Track visited gifts to prevent circular recipe issues
      const visited = new Set<string>([giftId])

      for (const ingredientId of ingredientIds) {
        const ingredientIdStr = String(ingredientId)
        // Skip circular references
        if (visited.has(ingredientIdStr)) {
          continue
        }
        visited.add(ingredientIdStr)
        // Only add if not already selected (avoid overwriting user's enhancement choice)
        if (!findEncodedGiftId(ingredientIdStr, newSelection)) {
          // Default cascaded gifts to base level (0)
          newSelection.add(encodeGiftSelection(0, ingredientIdStr))
        }
      }
    }

    onGiftSelectionChange(newSelection)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] overflow-hidden flex flex-col duration-100">
        <DialogHeader>
          <DialogTitle>
            {t('pages.plannerMD.comprehensiveEgoGiftList')}
          </DialogTitle>
        </DialogHeader>

        {/* Filter bar */}
        <div className="flex gap-4 justify-between items-center py-2">
          <div className="flex gap-4 items-center">
            <EGOGiftKeywordFilter
              selectedKeywords={selectedKeywords}
              onSelectionChange={setSelectedKeywords}
            />
            <Sorter sortMode={sortMode} onSortModeChange={setSortMode} />
          </div>
          <EGOGiftSearchBar
            searchQuery={searchQuery}
            onSearchChange={setSearchQuery}
          />
        </div>

        {/* Gift selection list */}
        <div className="flex-1 overflow-hidden">
          <EGOGiftSelectionList
            gifts={gifts}
            selectedKeywords={selectedKeywords}
            searchQuery={searchQuery}
            sortMode={sortMode}
            selectedGiftIds={selectedGiftIds}
            maxSelectable={Infinity}
            enableEnhancementSelection
            onEnhancementSelect={handleEnhancementSelect}
          />
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => { onGiftSelectionChange(new Set()) }}
          >
            {t('common.reset')}
          </Button>
          <Button onClick={() => { onOpenChange(false) }}>
            {t('common.done')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
