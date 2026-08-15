import { useState, useEffect, useRef, startTransition, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { LoadingState } from '@/components/feedback/LoadingState'

import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useEGOGiftListData } from '@/pages/egoGift'
import { EGOGiftFilterBar } from '@/pages/egoGift'
import { EGOGiftSelectionList } from '@/pages/egoGift'
import type { SortMode } from '@/shared/filter'
import { bucketAndSortFloorGifts } from '../../lib/floorGiftBucketing'
import { applyGiftToggle } from '../../lib/giftToggle'
import type { EGOGiftListItem } from '@/pages/egoGift'
import type { EnhancementLevel, DungeonIdx } from '@/shared/gameData'
import { SECTION_STYLES } from '@/lib/constants'
import { toGiftListItems } from '@/pages/egoGift'

interface FloorGiftSelectorPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  floorNumber: number
  themePackId: string
  difficulty: DungeonIdx
  selectedGiftIds: Set<string>
  onGiftSelectionChange: (giftIds: Set<string>) => void
}

/**
 * Dialog for selecting EGO gifts for a floor, filtered by theme pack.
 *
 * Bucketing (under themed-reachability semantics of `gift.themePack`):
 *   1. themed to this pack (exclusive + recipe-derived themed fusions)
 *   2. general AND directly in this pack's egoGiftPool
 *   3. general but not in this pack's pool (random-fusion acquirable)
 * Gifts themed to other packs only are hidden — genuinely unobtainable here.
 */
export function FloorGiftSelectorPane({
  open,
  onOpenChange,
  floorNumber,
  themePackId,
  difficulty,
  selectedGiftIds,
  onGiftSelectionChange,
}: FloorGiftSelectorPaneProps) {
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

  const sortedGifts = bucketAndSortFloorGifts(gifts, themePackId, difficulty, sortMode)

  // Read through a ref so the handler keeps one identity for the pane's lifetime.
  // Closing over the selection would give every gift cell a new callback on each
  // toggle, re-rendering all of them to change one card.
  const latest = useRef({ selectedGiftIds, specById, themePackId, onGiftSelectionChange })
  useEffect(() => {
    latest.current = { selectedGiftIds, specById, themePackId, onGiftSelectionChange }
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
          themePackId: packId,
          onGiftSelectionChange: notify,
        } = latest.current

        // A floor only carries what this theme pack can hand out
        const canCascade = (ingredientId: string) => {
          const ingredientSpec = specs.get(ingredientId)
          return (
            !ingredientSpec ||
            ingredientSpec.themePack.length === 0 ||
            ingredientSpec.themePack.includes(packId)
          )
        }

        notify(applyGiftToggle(current, giftId, enhancement, { specById: specs, canCascade }))
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
            <DialogTitle>
              {t('pages.plannerMD.selectEgoGiftsForFloor', { floor: floorNumber })}
            </DialogTitle>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  onGiftSelectionChange(new Set())
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
