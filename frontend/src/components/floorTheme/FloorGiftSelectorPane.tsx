import { useState, useMemo, useEffect, startTransition, useRef, useCallback } from 'react'
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
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { SearchBar } from '@/components/common/SearchBar'
import { EGOGiftSelectionList } from '@/components/egoGift/EGOGiftSelectionList'
import { EGOGiftKeywordFilter } from '@/components/egoGift/EGOGiftKeywordFilter'
import { Sorter, type SortMode } from '@/components/common/Sorter'
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
 * Returns gifts where themePack contains the selected ID OR is empty array
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
 * Shows gifts matching the floor's theme pack (or universal gifts).
 * State is managed by parent; this pane calls onGiftSelectionChange.
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

  // Defer content loading until dialog animation completes (only first time)
  const [contentReady, setContentReady] = useState(false)
  useEffect(() => {
    if (open && !contentReady) {
      const timer = setTimeout(() => setContentReady(true), 150)
      return () => clearTimeout(timer)
    }
  }, [open, contentReady])

  const { spec, i18n } = useEGOGiftListData()
  const { keywordToValue } = useSearchMappings()

  // Filter states (local to pane UI - reset on reopen)
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState('')
  const [sortMode, setSortMode] = useState<SortMode>('tier-first')

  // Reset filters when dialog closes to provide clean slate for next edit session
  useEffect(() => {
    if (!open) {
      setSelectedKeywords(new Set())
      setSearchQuery('')
      setSortMode('tier-first')
    }
  }, [open])

  // Get filtered gift IDs based on theme pack (skip until content ready)
  const giftIdFilter = useMemo(() => {
    if (!contentReady) return []
    return getFilteredGiftIds(spec, themePackId)
  }, [contentReady, spec, themePackId])

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
      maxEnhancement: specData.maxEnhancement,
      recipe: specData.recipe,
      hardOnly: specData.hardOnly,
      extremeOnly: specData.extremeOnly,
    }))
  }, [contentReady, spec, i18n])

  // Build O(1) lookup map for recipe cascade selection (skip until content ready)
  const specById = useMemo(() => {
    if (!contentReady) return new Map()
    return new Map(Object.entries(spec))
  }, [contentReady, spec])

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

  // Compute visible IDs (CSS filtering)
  const visibleIds = useMemo(() => {
    const ids = new Set<string>()
    for (const gift of sortedGifts) {
      // Keyword filter (treat null keyword as 'None')
      if (selectedKeywords.size > 0) {
        const giftKeyword = gift.keyword ?? 'None'
        if (!selectedKeywords.has(giftKeyword)) continue
      }

      // Search filter - match name OR keyword (treat null keyword as 'None')
      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()
        const nameMatch = gift.name?.toLowerCase().includes(lowerQuery)
        const giftKeyword = gift.keyword ?? 'None'
        const keywordMatch = Array.from(keywordToValue.entries()).some(([naturalLang, pascalValues]) => {
          if (naturalLang.includes(lowerQuery)) {
            return pascalValues.includes(giftKeyword)
          }
          return false
        })
        if (!nameMatch && !keywordMatch) continue
      }

      ids.add(gift.id)
    }
    return ids
  }, [sortedGifts, selectedKeywords, searchQuery, keywordToValue])

  // Use ref to always access latest state in stable callback
  const selectedGiftIdsRef = useRef(selectedGiftIds)
  selectedGiftIdsRef.current = selectedGiftIds
  const comprehensiveGiftIdsRef = useRef(comprehensiveGiftIds ?? new Set<string>())
  comprehensiveGiftIdsRef.current = comprehensiveGiftIds ?? new Set<string>()

  /**
   * Handle enhancement selection with toggle logic and cascade:
   * - No selection + click level -> select gift with that level + cascade ingredients
   * - Selected + click same level -> deselect gift (no reverse cascade)
   * - Selected + click different level -> change enhancement level
   *
   * Also syncs selections to comprehensiveGiftIds for consistency (when in store context).
   * Uses ref pattern for stable callback with fresh state.
   */
  const handleEnhancementSelect = useCallback((giftId: string, enhancement: EnhancementLevel) => {
    startTransition(() => {
      const current = selectedGiftIdsRef.current
      const currentComprehensive = comprehensiveGiftIdsRef.current
      const newSelection = new Set(current)
      const newComprehensive = new Set(currentComprehensive)

      const existingEncodedId = findEncodedGiftId(giftId, current)

      if (existingEncodedId) {
        // Gift is already selected in this floor
        const { enhancement: currentEnhancement } = decodeGiftSelection(existingEncodedId)

        if (currentEnhancement === enhancement) {
          // Same level clicked - deselect from both floor and comprehensive
          newSelection.delete(existingEncodedId)
          newComprehensive.delete(existingEncodedId)
        } else {
          // Different level clicked - update enhancement in floor only
          // (comprehensive keeps both levels for tracking acquisition vs enhancement)
          newSelection.delete(existingEncodedId)
          const newEncodedId = encodeGiftSelection(enhancement, giftId)
          newSelection.add(newEncodedId)
        }
      } else {
        // Gift not selected in this floor - add with new enhancement
        const newEncodedId = encodeGiftSelection(enhancement, giftId)
        newSelection.add(newEncodedId)

        // Add to comprehensive (allows multiple enhancement levels for same gift)
        // This supports MD gameplay: acquire on floor 1, enhance on floor 3
        newComprehensive.add(newEncodedId)

        // Cascade-select recipe ingredients (if any)
        const giftSpec = specById.get(giftId)
        if (giftSpec) {
          const ingredientIds = getCascadeIngredients(giftSpec.recipe)
          // Track visited gifts to prevent circular recipe issues
          const visited = new Set<string>([giftId])

          for (const ingredientId of ingredientIds) {
            const ingredientIdStr = String(ingredientId)
            // Skip circular references
            if (visited.has(ingredientIdStr)) continue
            visited.add(ingredientIdStr)

            // Add to floor selection if not already selected
            if (!findEncodedGiftId(ingredientIdStr, newSelection)) {
              newSelection.add(encodeGiftSelection(0, ingredientIdStr))
            }
            // Add to comprehensive (allows tracking ingredient acquisition)
            const ingredientEncodedId = encodeGiftSelection(0, ingredientIdStr)
            newComprehensive.add(ingredientEncodedId)
          }
        }
      }

      onGiftSelectionChange(newSelection)
      // Only sync to comprehensive if store is available (not in tracker mode)
      if (setComprehensiveGiftIds) {
        setComprehensiveGiftIds(newComprehensive)
      }
    })
  }, [onGiftSelectionChange, setComprehensiveGiftIds, specById])

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
          {...(contentReady && { forceMount: true })}
          onPointerDownOutside={(e) => e.preventDefault()}
        >
          <DialogHeader>
            <DialogTitle>
              {t('pages.plannerMD.selectEgoGiftsForFloor', { floor: floorNumber })}
            </DialogTitle>
          </DialogHeader>

          {/* Filter bar */}
          <div className="flex flex-col sm:flex-row gap-4 sm:justify-between sm:items-center py-2">
            <div className="flex flex-col sm:flex-row gap-4 sm:items-center min-w-0">
              <div className="min-w-0">
                <EGOGiftKeywordFilter
                  selectedKeywords={selectedKeywords}
                  onSelectionChange={setSelectedKeywords}
                />
              </div>
              <Sorter sortMode={sortMode} onSortModeChange={setSortMode} />
            </div>
            <div className="min-w-0 sm:shrink-0">
              <SearchBar
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                placeholder={t('deckBuilder.egoGiftSearchPlaceholder')}
              />
            </div>
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
