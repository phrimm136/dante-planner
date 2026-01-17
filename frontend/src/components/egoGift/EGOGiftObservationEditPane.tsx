import { useState, useEffect, useMemo, useDeferredValue, startTransition, useRef, useCallback } from 'react'
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
import { useEGOGiftObservationData } from '@/hooks/useEGOGiftObservationData'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { usePlannerEditorStore } from '@/stores/usePlannerEditorStore'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { SortMode } from '@/components/common/Sorter'
import { Sorter } from '@/components/common/Sorter'
import { SearchBar } from '@/components/common/SearchBar'
import { StarlightCostDisplay } from '@/components/common/StarlightCostDisplay'
import { sortEGOGifts } from '@/lib/egoGiftSort'
import { EGOGiftSelectionList } from './EGOGiftSelectionList'
import { EGOGiftObservationSelection } from './EGOGiftObservationSelection'
import { EGOGiftKeywordFilter } from './EGOGiftKeywordFilter'
import { MAX_OBSERVABLE_GIFTS } from '@/lib/constants'

interface EGOGiftObservationEditPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

/**
 * Dialog for editing EGO Gift Observation selection
 * Desktop: 9:1 grid (selection list left, selected gifts right)
 * Mobile: stacked layout
 * Local filter state resets on dialog close
 * Suspends while loading - wrap in Suspense boundary
 */
export function EGOGiftObservationEditPane({
  open,
  onOpenChange,
}: EGOGiftObservationEditPaneProps) {
  // Store state
  const selectedGiftIds = usePlannerEditorStore((s) => s.observationGiftIds)
  const setObservationGiftIds = usePlannerEditorStore((s) => s.setObservationGiftIds)
  const { t } = useTranslation(['planner', 'common'])


  // Defer content loading until dialog animation completes (only first time)
  const [contentReady, setContentReady] = useState(false)
  useEffect(() => {
    if (open && !contentReady) {
      // Wait for dialog animation to complete (duration-100 = 100ms + 150ms buffer)
      const timer = setTimeout(() => setContentReady(true), 250)
      return () => clearTimeout(timer)
    }
  }, [open, contentReady])

  // Load observation data (suspends while loading)
  const { data: observationData } = useEGOGiftObservationData()
  const { spec, i18n } = useEGOGiftListData()
  const { keywordToValue } = useSearchMappings()

  // LOCAL filter states (must declare before useMemos that use them)
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState('')
  const [sortMode, setSortMode] = useState<SortMode>('tier-first')

  // Merge spec and i18n into EGOGiftListItem array
  const gifts = useMemo<EGOGiftListItem[]>(() => {
    if (!contentReady) return [] // Wait for dialog animation first
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

  // Sort gifts (apply giftIdFilter + sort) - DeckBuilder pattern
  const sortedGifts = useMemo(() => {
    let filtered = gifts
    // Apply ID filter (observation eligible gifts)
    if (observationData.observationEgoGiftDataList.length > 0) {
      const idSet = new Set(observationData.observationEgoGiftDataList.map(String))
      filtered = filtered.filter((gift) => idSet.has(gift.id))
    }
    return sortEGOGifts(filtered, sortMode)
  }, [gifts, observationData.observationEgoGiftDataList, sortMode])

  // Compute visible IDs (CSS filtering) - IdentityList pattern
  const visibleIds = useMemo(() => {
    const ids = new Set<string>()
    for (const gift of sortedGifts) {
      // Keyword filter - gift keyword must match ANY selected keyword (OR logic)
      if (selectedKeywords.size > 0) {
        if (!gift.keyword || !selectedKeywords.has(gift.keyword)) continue
      }

      // Search filter - match name OR keyword
      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()
        // Check name match (partial, case-insensitive)
        const nameMatch = gift.name?.toLowerCase().includes(lowerQuery)
        // Check keyword match (partial match on natural language, then lookup PascalCase values)
        const keywordMatch = Array.from(keywordToValue.entries()).some(([naturalLang, pascalValues]) => {
          if (naturalLang.includes(lowerQuery)) {
            return gift.keyword && pascalValues.includes(gift.keyword)
          }
          return false
        })
        // Must match at least one
        if (!nameMatch && !keywordMatch) continue
      }

      ids.add(gift.id)
    }
    return ids
  }, [sortedGifts, selectedKeywords, searchQuery, keywordToValue])

  // Defer list rendering (DeckBuilder pattern) - selection stays synchronous!
  const deferredGifts = useDeferredValue(sortedGifts)

  // Reset filters when dialog closes to provide clean slate for next edit session
  // Trade-off: Users lose filter state, but prevents confusion from invisible filters
  useEffect(() => {
    if (!open) {
      setSelectedKeywords(new Set())
      setSearchQuery('')
      setSortMode('tier-first')
    }
  }, [open])

  // Use ref to always access latest selectedGiftIds in stable callback
  // This prevents stale closure issues when memoized children skip re-render
  const selectedGiftIdsRef = useRef(selectedGiftIds)
  selectedGiftIdsRef.current = selectedGiftIds

  // Stable callback - uses ref to get latest state, so memoized children work correctly
  const handleGiftToggle = useCallback((giftId: string) => {
    startTransition(() => {
      const current = selectedGiftIdsRef.current
      const newSelection = new Set(current)
      if (newSelection.has(giftId)) {
        newSelection.delete(giftId)
      } else if (newSelection.size < MAX_OBSERVABLE_GIFTS) {
        newSelection.add(giftId)
      }
      setObservationGiftIds(newSelection)
    })
  }, [setObservationGiftIds])

  // Calculate current cost from observation data
  const currentCost =
    observationData.observationEgoGiftCostDataList.find(
      (cost) => cost.egogiftCount === selectedGiftIds.size
    )?.starlightCost || 0

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
          className="max-w-[30em] lg:max-w-[1440px] duration-100"
          {...(contentReady && { forceMount: true })}
          onPointerDownOutside={(e) => e.preventDefault()}
        >
          <DialogHeader>
            <DialogTitle>{t('pages.plannerMD.egoGiftObservation')}</DialogTitle>
          </DialogHeader>

          {/* Cost display at top-right */}
          <div className="flex justify-end mb-4">
            <StarlightCostDisplay cost={currentCost} size="lg" />
          </div>

          {/* Filter row: keyword filter, sorter, search bar */}
          <div className="flex flex-col sm:flex-row gap-4 sm:justify-between">
            {/* Left side: Filters and Sorter */}
            <div className="flex flex-col sm:flex-row gap-4 sm:items-center min-w-0">
              <div className="min-w-0">
                <EGOGiftKeywordFilter
                  selectedKeywords={selectedKeywords}
                  onSelectionChange={setSelectedKeywords}
                />
              </div>
              <Sorter sortMode={sortMode} onSortModeChange={setSortMode} />
            </div>

            {/* Right side: Search bar */}
            <div className="min-w-0 sm:shrink-0">
              <SearchBar
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                placeholder={t('deckBuilder.egoGiftSearchPlaceholder')}
              />
            </div>
          </div>

          {/* Main content: Desktop 9:1 grid, Mobile stacked */}
          <div className="grid grid-cols-1 lg:grid-cols-10 gap-4">
            {/* Left: Selection List (9 columns on desktop) */}
            <div className="lg:col-span-9">
              <h3 className="text-lg font-medium mb-2">{t('pages.plannerMD.selectEgoGifts')}</h3>
              {contentReady && deferredGifts.length > 0 ? (
                <EGOGiftSelectionList
                  gifts={deferredGifts}
                  visibleIds={visibleIds}
                  selectedGiftIds={selectedGiftIds}
                  maxSelectable={MAX_OBSERVABLE_GIFTS}
                  onGiftSelect={handleGiftToggle}
                />
              ) : (
                <div className="bg-muted border border-border rounded-md p-6 h-[350px]">
                  <div className="grid grid-cols-[repeat(auto-fill,minmax(96px,1fr))] gap-3">
                    {Array.from({ length: 30 }).map((_, i) => (
                      <Skeleton key={i} className="w-24 h-24 rounded-md" />
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Right: Selected Gifts (1 column on desktop) */}
            <div className="lg:col-span-1 w-32">
              <h3 className="text-lg font-medium mb-2">{t('pages.plannerMD.selectedEgoGifts')}</h3>
              <EGOGiftObservationSelection
                selectedGiftIds={Array.from(selectedGiftIds)}
                onGiftRemove={handleGiftToggle}
              />
            </div>
          </div>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setObservationGiftIds(new Set())
              }}
            >
              {t('common:reset')}
            </Button>
            <Button
              onClick={() => {
                onOpenChange(false)
              }}
            >
              {t('common:done')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
