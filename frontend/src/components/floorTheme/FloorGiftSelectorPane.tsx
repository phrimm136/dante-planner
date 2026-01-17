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
import { encodeGiftSelection, getBaseGiftId } from '@/lib/egoGiftEncoding'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EnhancementLevel } from '@/lib/constants'

interface FloorGiftSelectorPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  floorNumber: number
  themePackId: string
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
  selectedGiftIds,
  onGiftSelectionChange,
}: FloorGiftSelectorPaneProps) {
  const { t } = useTranslation(['planner', 'common'])

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
    }))
  }, [contentReady, spec, i18n])

  // Sort gifts (apply giftIdFilter + sort)
  const sortedGifts = useMemo(() => {
    const idSet = new Set(giftIdFilter.map(String))
    const filtered = gifts.filter((gift) => idSet.has(gift.id))
    return sortEGOGifts(filtered, sortMode)
  }, [gifts, giftIdFilter, sortMode])

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

  // Use ref to always access latest selectedGiftIds in stable callback
  const selectedGiftIdsRef = useRef(selectedGiftIds)
  selectedGiftIdsRef.current = selectedGiftIds

  /**
   * Handle enhancement selection with toggle logic:
   * - No selection + click level -> select gift with that level
   * - Selected + click same level -> deselect gift
   * - Selected + click different level -> change enhancement level
   * Uses ref pattern for stable callback with fresh state
   */
  const handleEnhancementSelect = useCallback((giftId: string, enhancement: EnhancementLevel) => {
    startTransition(() => {
      const current = selectedGiftIdsRef.current
      const newSelection = new Set(current)

      // Remove any existing selection for this gift (any enhancement level)
      for (const encodedId of current) {
        if (getBaseGiftId(encodedId) === giftId) {
          newSelection.delete(encodedId)
          break
        }
      }

      // Add new selection with enhancement (toggle off if clicking same)
      const encodedId = encodeGiftSelection(enhancement, giftId)
      if (!current.has(encodedId)) {
        newSelection.add(encodedId)
      }

      onGiftSelectionChange(newSelection)
    })
  }, [onGiftSelectionChange])

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
