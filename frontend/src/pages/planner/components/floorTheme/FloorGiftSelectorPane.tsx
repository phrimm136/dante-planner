import { useState, useMemo, useEffect, startTransition, useRef, useCallback } from 'react'
import { useTranslation } from 'react-i18next'

import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useEGOGiftListData } from '@/pages/egoGift'
import { EGOGiftFilterBar } from '@/pages/egoGift'
import { EGOGiftSelectionList } from '@/pages/egoGift'
import type { SortMode } from '@/shared/filter'
import { bucketAndSortFloorGifts } from '../../lib/floorGiftBucketing'
import {
  encodeGiftSelection,
  findEncodedGiftId,
  decodeGiftSelection,
  getCascadeIngredients,
} from '@/pages/egoGift'
import type { EGOGiftListItem } from '@/pages/egoGift'
import type { EnhancementLevel, DungeonIdx } from '@/shared/gameData'

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
  const gifts = useMemo<EGOGiftListItem[]>(() => {
    return Object.entries(spec).map(([id, specData]) => ({
      id,
      name: i18n[id] || id,
      tag: specData.tag as EGOGiftListItem['tag'],
      keyword: specData.keyword,
      battleKeywordList: specData.battleKeywordList ?? [],
      attributeType: specData.attributeType,
      themePack: specData.themePack,
      maxEnhancement: specData.maxEnhancement,
      recipe: specData.recipe,
      hardOnly: specData.hardOnly,
      extremeOnly: specData.extremeOnly,
    }))
  }, [spec, i18n])

  // Build O(1) lookup map for recipe cascade selection
  const specById = useMemo(() => {
    return new Map(Object.entries(spec))
  }, [spec])

  const sortedGifts = useMemo(
    () => bucketAndSortFloorGifts(gifts, themePackId, difficulty, sortMode),
    [gifts, sortMode, themePackId, difficulty],
  )

  // Use ref to always access latest state in stable callback
  const selectedGiftIdsRef = useRef(selectedGiftIds)
  selectedGiftIdsRef.current = selectedGiftIds

  /**
   * Handle enhancement selection with toggle logic and cascade
   */
  const handleEnhancementSelect = useCallback(
    (giftId: string, enhancement: EnhancementLevel) => {
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
            const newEncodedId = encodeGiftSelection(enhancement, giftId)
            newSelection.add(newEncodedId)
          }
        } else {
          const newEncodedId = encodeGiftSelection(enhancement, giftId)
          newSelection.add(newEncodedId)

          const giftSpec = specById.get(giftId)
          if (giftSpec) {
            const ingredientIds = getCascadeIngredients(giftSpec.recipe)
            const visited = new Set<string>([giftId])

            for (const ingredientId of ingredientIds) {
              const ingredientIdStr = String(ingredientId)
              if (visited.has(ingredientIdStr)) continue
              visited.add(ingredientIdStr)

              const ingredientSpec = specById.get(ingredientIdStr)
              const isObtainable =
                !ingredientSpec ||
                ingredientSpec.themePack.length === 0 ||
                ingredientSpec.themePack.includes(themePackId)

              // Only add to floor if obtainable in this theme pack
              if (isObtainable && !findEncodedGiftId(ingredientIdStr, newSelection)) {
                newSelection.add(encodeGiftSelection(0, ingredientIdStr))
              }
            }
          }
        }

        onGiftSelectionChange(newSelection)
      })
    },
    [onGiftSelectionChange, specById, themePackId],
  )

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] flex flex-col"
        showCloseButton={false}
      >
        <DialogHeader>
          <div className="flex items-center justify-between">
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
