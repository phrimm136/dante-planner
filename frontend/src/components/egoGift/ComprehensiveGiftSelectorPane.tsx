import { useState, useMemo, useEffect, startTransition } from 'react'
import { useTranslation } from 'react-i18next'

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import {
  encodeGiftSelection,
  decodeGiftSelection,
  findEncodedGiftId,
  getCascadeIngredients,
} from '@/lib/egoGiftEncoding'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EnhancementLevel } from '@/lib/constants'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { sortEGOGifts } from '@/lib/egoGiftSort'
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
  const { t } = useTranslation(['planner', 'common'])

  // Defer content loading until dialog animation completes (only first time)
  const [contentReady, setContentReady] = useState(false)
  useEffect(() => {
    if (open && !contentReady) {
      // Wait for dialog animation to complete (duration-100 = 100ms + 50ms buffer)
      const timer = setTimeout(() => setContentReady(true), 150)
      return () => clearTimeout(timer)
    }
  }, [open, contentReady])

  const { spec, i18n } = useEGOGiftListData()
  const { keywordToValue } = useSearchMappings()

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

  // Convert to EGOGiftListItem array (skip until content ready)
  const gifts = useMemo<EGOGiftListItem[]>(() => {
    if (!contentReady) return []
    return Object.entries(spec).map(([id, specData]) => ({
      id,
      name: i18n[id] || id,
      tag: specData.tag as EGOGiftListItem['tag'],
      keyword: specData.keyword,
      attributeType: specData.attributeType,
      themePack: specData.themePack,
      recipe: specData.recipe,
    }))
  }, [contentReady, spec, i18n])

  // Build O(1) lookup map for recipe cascade selection (skip until content ready)
  const specById = useMemo(() => {
    if (!contentReady) return new Map()
    return new Map(Object.entries(spec))
  }, [contentReady, spec])

  // Sort gifts (no ID filter for comprehensive list)
  const sortedGifts = useMemo(() => {
    return sortEGOGifts(gifts, sortMode)
  }, [gifts, sortMode])

  // Compute visible IDs (CSS filtering)
  const visibleIds = useMemo(() => {
    const ids = new Set<string>()
    for (const gift of sortedGifts) {
      // Keyword filter
      if (selectedKeywords.size > 0) {
        if (!gift.keyword || !selectedKeywords.has(gift.keyword)) continue
      }

      // Search filter - match name OR keyword
      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()
        const nameMatch = gift.name?.toLowerCase().includes(lowerQuery)
        const keywordMatch = Array.from(keywordToValue.entries()).some(([naturalLang, pascalValues]) => {
          if (naturalLang.includes(lowerQuery)) {
            return gift.keyword && pascalValues.includes(gift.keyword)
          }
          return false
        })
        if (!nameMatch && !keywordMatch) continue
      }

      ids.add(gift.id)
    }
    return ids
  }, [sortedGifts, selectedKeywords, searchQuery, keywordToValue])

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
    startTransition(() => {
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
    })
  }

  return (
    <>
      {/* Custom backdrop to block background interaction */}
      {open && (
        <div
          className="fixed inset-0 z-50 bg-black/50 animate-in fade-in-0"
          onClick={() => onOpenChange(false)}
        />
      )}

      <Dialog open={open} onOpenChange={onOpenChange} modal={false}>
        <DialogContent
          className="max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] overflow-hidden flex flex-col duration-100"
          forceMount={contentReady}
          onPointerDownOutside={(e) => e.preventDefault()}
        >
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
          {contentReady ? (
            <EGOGiftSelectionList
              gifts={sortedGifts}
              visibleIds={visibleIds}
              selectedGiftIds={selectedGiftIds}
              maxSelectable={Infinity}
              enableEnhancementSelection
              onEnhancementSelect={handleEnhancementSelect}
            />
          ) : (
            <div className="bg-muted border border-border rounded-md p-6 h-full overflow-y-auto scrollbar-hide">
              <div className="grid grid-cols-[repeat(auto-fill,minmax(96px,1fr))] gap-3">
                {Array.from({ length: 20 }).map((_, i) => (
                  <Skeleton key={i} className="w-24 h-24 rounded-md" />
                ))}
              </div>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => { onGiftSelectionChange(new Set()) }}
          >
            {t('common:reset')}
          </Button>
          <Button onClick={() => { onOpenChange(false) }}>
            {t('common:done')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
    </>
  )
}
