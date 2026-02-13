import { useState, useMemo, useEffect, startTransition, useRef, useCallback } from 'react'
import { useTranslation } from 'react-i18next'

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { EGOGiftFilterBar } from '@/components/egoGift/EGOGiftFilterBar'
import { EGOGiftSelectionList } from '@/components/egoGift/EGOGiftSelectionList'
import type { SortMode } from '@/components/common/Sorter'
import { sortEGOGifts } from '@/lib/egoGiftSort'
import {
  encodeGiftSelection,
  findEncodedGiftId,
  decodeGiftSelection,
  getCascadeIngredients,
} from '@/lib/egoGiftEncoding'
import { usePlannerEditorStoreSafe } from '@/stores/usePlannerEditorStore'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EnhancementLevel, DungeonIdx } from '@/lib/constants'
import { DUNGEON_IDX } from '@/lib/constants'

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
 * Gets gift IDs that are allowed for the floor based on theme pack
 */
function getFilteredGiftIds(
  spec: Record<string, { themePack: string[] }>,
  themePackId: string
): number[] {
  const allowedIds: number[] = []
  for (const [id, giftSpec] of Object.entries(spec)) {
    const themePack = giftSpec.themePack
    if (!themePack || themePack.length === 0 || themePack.includes(themePackId)) {
      allowedIds.push(Number(id))
    }
  }
  return allowedIds
}

/**
 * Dialog for selecting EGO gifts for a floor, filtered by theme pack.
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

  // Store state for comprehensive gift sync (safe - returns undefined outside context)
  const comprehensiveGiftIds = usePlannerEditorStoreSafe((s) => s.comprehensiveGiftIds)
  const setComprehensiveGiftIds = usePlannerEditorStoreSafe((s) => s.setComprehensiveGiftIds)

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

  // Get filtered gift IDs based on theme pack
  const giftIdFilter = useMemo(() => {
    return getFilteredGiftIds(spec, themePackId)
  }, [spec, themePackId])

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
      hardOnly: specData.hardOnly,
      extremeOnly: specData.extremeOnly,
    }))
  }, [spec, i18n])

  // Build O(1) lookup map for recipe cascade selection
  const specById = useMemo(() => {
    return new Map(Object.entries(spec))
  }, [spec])

  // Sort gifts: filter by difficulty, then theme pack specific first, then universal
  const sortedGifts = useMemo(() => {
    const idSet = new Set(giftIdFilter.map(String))

    // Filter by theme pack allowlist
    const themeFiltered = gifts.filter((gift) => idSet.has(gift.id))

    // Filter by difficulty: NORMAL < HARD < EXTREME
    const difficultyFiltered = themeFiltered.filter((gift) => {
      if (gift.extremeOnly && difficulty < DUNGEON_IDX.EXTREME) return false
      if (gift.hardOnly && difficulty < DUNGEON_IDX.HARD) return false
      return true
    })

    // Split: theme pack specific first, then universal
    const themePackSpecific = difficultyFiltered.filter((g) => g.themePack?.includes(themePackId))
    const universal = difficultyFiltered.filter((g) => !g.themePack || g.themePack.length === 0)

    return [...sortEGOGifts(themePackSpecific, sortMode), ...sortEGOGifts(universal, sortMode)]
  }, [gifts, giftIdFilter, sortMode, themePackId, difficulty])

  // Use ref to always access latest state in stable callback
  const selectedGiftIdsRef = useRef(selectedGiftIds)
  selectedGiftIdsRef.current = selectedGiftIds
  const comprehensiveGiftIdsRef = useRef(comprehensiveGiftIds ?? new Set<string>())
  comprehensiveGiftIdsRef.current = comprehensiveGiftIds ?? new Set<string>()

  /**
   * Handle enhancement selection with toggle logic and cascade
   */
  const handleEnhancementSelect = useCallback((giftId: string, enhancement: EnhancementLevel) => {
    startTransition(() => {
      const current = selectedGiftIdsRef.current
      const currentComprehensive = comprehensiveGiftIdsRef.current
      const newSelection = new Set(current)
      const newComprehensive = new Set(currentComprehensive)

      const existingEncodedId = findEncodedGiftId(giftId, current)

      if (existingEncodedId) {
        const { enhancement: currentEnhancement } = decodeGiftSelection(existingEncodedId)

        if (currentEnhancement === enhancement) {
          newSelection.delete(existingEncodedId)
          newComprehensive.delete(existingEncodedId)
        } else {
          newSelection.delete(existingEncodedId)
          const newEncodedId = encodeGiftSelection(enhancement, giftId)
          newSelection.add(newEncodedId)
        }
      } else {
        const newEncodedId = encodeGiftSelection(enhancement, giftId)
        newSelection.add(newEncodedId)
        newComprehensive.add(newEncodedId)

        const giftSpec = specById.get(giftId)
        if (giftSpec) {
          const ingredientIds = getCascadeIngredients(giftSpec.recipe)
          const visited = new Set<string>([giftId])

          for (const ingredientId of ingredientIds) {
            const ingredientIdStr = String(ingredientId)
            if (visited.has(ingredientIdStr)) continue
            visited.add(ingredientIdStr)

            if (!findEncodedGiftId(ingredientIdStr, newSelection)) {
              newSelection.add(encodeGiftSelection(0, ingredientIdStr))
            }
            const ingredientEncodedId = encodeGiftSelection(0, ingredientIdStr)
            newComprehensive.add(ingredientEncodedId)
          }
        }
      }

      onGiftSelectionChange(newSelection)
      if (setComprehensiveGiftIds) {
        setComprehensiveGiftIds(newComprehensive)
      }
    })
  }, [onGiftSelectionChange, setComprehensiveGiftIds, specById])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] flex flex-col" showCloseButton={false}>
          <DialogHeader>
            <div className="flex items-center justify-between">
              <DialogTitle>
                {t('pages.plannerMD.selectEgoGiftsForFloor', { floor: floorNumber })}
              </DialogTitle>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => { onGiftSelectionChange(new Set()) }}
                >
                  {t('common:reset')}
                </Button>
                <Button size="sm" onClick={() => { onOpenChange(false) }}>
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
