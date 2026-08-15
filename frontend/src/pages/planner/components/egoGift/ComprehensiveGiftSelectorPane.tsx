import { useState, useEffect, useRef, startTransition, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { LoadingState } from '@/components/feedback/LoadingState'

import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { encodeGiftSelection, buildSelectionLookup, getCascadeIngredients } from '@/pages/egoGift'
import type { EGOGiftListItem } from '@/pages/egoGift'
import type { EnhancementLevel } from '@/shared/gameData'
import { useEGOGiftListData } from '@/pages/egoGift'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import { sortEGOGifts } from '@/pages/egoGift'
import { EGOGiftFilterBar } from '@/pages/egoGift'
import { EGOGiftSelectionList } from '@/pages/egoGift'
import type { SortMode } from '@/shared/filter'
import { SECTION_STYLES } from '@/lib/constants'
import { toGiftListItems } from '@/pages/egoGift'

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
  const gifts: EGOGiftListItem[] = (() => {
    return toGiftListItems(spec, i18n)
  })()

  // Build O(1) lookup map for recipe cascade selection
  const specById = (() => {
    return new Map(Object.entries(spec))
  })()

  // Sort gifts (no ID filter for comprehensive list)
  const sortedGifts = (() => {
    return sortEGOGifts(gifts, sortMode)
  })()

  // Read through a ref so the handler keeps one identity for the pane's lifetime.
  // Closing over the selection would give every gift cell a new callback on each
  // toggle, re-rendering all of them to change one card.
  const latest = useRef({ selectedGiftIds, specById, setComprehensiveGiftIds })
  useEffect(() => {
    latest.current = { selectedGiftIds, specById, setComprehensiveGiftIds }
  })

  /**
   * Handle enhancement selection with toggle logic and cascade
   */
  const [handleEnhancementSelect] = useState(
    () => (giftId: string, enhancement: EnhancementLevel) => {
      startTransition(() => {
        const {
          selectedGiftIds: current,
          specById: specs,
          setComprehensiveGiftIds: notify,
        } = latest.current
        const newSelection = new Set(current)
        const selectionLookup = buildSelectionLookup(current)
        const existing = selectionLookup.get(giftId)

        if (existing) {
          newSelection.delete(existing.encodedId)
          if (existing.enhancement !== enhancement) {
            newSelection.add(encodeGiftSelection(enhancement, giftId))
          }
        } else {
          newSelection.add(encodeGiftSelection(enhancement, giftId))

          const giftSpec = specs.get(giftId)
          if (!giftSpec) {
            notify(newSelection)
            return
          }

          const ingredientIds = getCascadeIngredients(giftSpec.recipe)
          const visited = new Set<string>([giftId])

          for (const ingredientId of ingredientIds) {
            const ingredientIdStr = String(ingredientId)
            if (visited.has(ingredientIdStr)) continue
            visited.add(ingredientIdStr)
            if (!selectionLookup.has(ingredientIdStr)) {
              newSelection.add(encodeGiftSelection(0, ingredientIdStr))
            }
          }
        }

        notify(newSelection)
      })
    },
  )

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] flex flex-col"
        showCloseButton={false}
      >
        <DialogHeader>
          <div className={SECTION_STYLES.LAYOUT.rowBetween}>
            <DialogTitle>{t('pages.plannerMD.comprehensiveEgoGiftList')}</DialogTitle>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setComprehensiveGiftIds(new Set())
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
        </DialogHeader>

        <EGOGiftFilterBar
          className="py-2"
          selectedKeywords={selectedKeywords}
          onKeywordsChange={setSelectedKeywords}
          sortMode={sortMode}
          onSortModeChange={setSortMode}
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
        />

        {/* Gift selection list */}
        <div className="flex-1 overflow-y-auto">
          <Suspense fallback={<LoadingState />}>
            <EGOGiftSelectionList
              gifts={sortedGifts}
              selectedKeywords={selectedKeywords}
              searchQuery={searchQuery}
              selectedGiftIds={selectedGiftIds}
              enableEnhancementSelection
              onEnhancementSelect={handleEnhancementSelect}
            />
          </Suspense>
        </div>
      </DialogContent>
    </Dialog>
  )
}
