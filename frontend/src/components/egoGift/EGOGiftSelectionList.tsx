import { useMemo } from 'react'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { SortMode } from '@/components/common/Sorter'
import type { EnhancementLevel } from '@/lib/constants'
import { CARD_GRID } from '@/lib/constants'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { sortEGOGifts } from '@/lib/egoGiftSort'
import { buildSelectionLookup } from '@/lib/egoGiftEncoding'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { EGOGiftSelectableCard } from './EGOGiftSelectableCard'
import { EGOGiftObservationCard } from './EGOGiftObservationCard'

interface EGOGiftSelectionListProps {
  gifts: EGOGiftListItem[]
  giftIdFilter?: number[]
  selectedKeywords: Set<string>
  searchQuery: string
  sortMode: SortMode
  selectedGiftIds: Set<string>
  maxSelectable: number
  /** Callback for standard selection mode (observation, start gifts) */
  onGiftSelect?: (giftId: string) => void
  /** Enable enhancement selection mode (for comprehensive list) */
  enableEnhancementSelection?: boolean
  /** Callback for enhancement selection (required when enableEnhancementSelection is true) */
  onEnhancementSelect?: (giftId: string, enhancement: EnhancementLevel) => void
}
/**
 * Universal EGO gift selection list with filtering and selection
 * Supports ID filtering, keyword filtering, search, and sorting
 * Used for observation, start gifts, and comprehensive gift selection
 */
export function EGOGiftSelectionList({
  gifts,
  giftIdFilter,
  selectedKeywords,
  searchQuery,
  sortMode,
  selectedGiftIds,
  maxSelectable,
  onGiftSelect,
  enableEnhancementSelection = false,
  onEnhancementSelect,
}: EGOGiftSelectionListProps) {
  const { keywordToValue } = useSearchMappings()

  // Build O(1) lookup map for enhancement selection mode (avoids O(n) iteration per card)
  const selectionLookup = useMemo(
    () => (enableEnhancementSelection ? buildSelectionLookup(selectedGiftIds) : null),
    [enableEnhancementSelection, selectedGiftIds]
  )
  // Apply ID filter first if provided
  let filtered = gifts
  if (giftIdFilter && giftIdFilter.length > 0) {
    const idSet = new Set(giftIdFilter.map(String))
    filtered = filtered.filter((gift) => idSet.has(gift.id))
  }
  // Filter gifts based on keyword and search query
  filtered = filtered.filter((gift) => {
    // Keyword filter - gift keyword must match ANY selected keyword (OR logic)
    if (selectedKeywords.size > 0) {
      const keywordMatches = gift.keyword && selectedKeywords.has(gift.keyword)
      if (!keywordMatches) {
        return false
      }
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
      if (!nameMatch && !keywordMatch) {
        return false
      }
    }
    return true
  })
  // Sort filtered gifts
  const displayedGifts = sortEGOGifts(filtered, sortMode)
  if (displayedGifts.length === 0) {
    return (
      <div className="bg-muted border border-border rounded-md p-6">
        <div className="text-center text-gray-500 py-8">
          No EGO Gifts match your current filters and search criteria
        </div>
      </div>
    )
  }
  return (
    <div className="bg-muted border border-border rounded-md p-6 h-[350px] overflow-y-auto scrollbar-hide">
      <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.EGO_GIFT}>
        {displayedGifts.map((gift) => {
          // Enhancement selection mode (comprehensive list)
          // Uses O(1) Map lookup instead of O(n) Set iteration
          if (enableEnhancementSelection && onEnhancementSelect && selectionLookup) {
            const entry = selectionLookup.get(gift.id)
            const selected = entry !== undefined
            const enhancement = entry?.enhancement ?? 0
            return (
              <EGOGiftSelectableCard
                key={gift.id}
                gift={gift}
                enhancement={enhancement}
                isSelected={selected}
                onEnhancementSelect={onEnhancementSelect}
              />
            )
          }

          // Standard selection mode (observation, start gifts)
          // Requires onGiftSelect callback
          if (!onGiftSelect) return null

          const isSelected = selectedGiftIds.has(gift.id)
          const canSelect = isSelected || selectedGiftIds.size < maxSelectable
          return (
            <EGOGiftObservationCard
              key={gift.id}
              gift={gift}
              isSelected={isSelected}
              isSelectable={canSelect}
              onSelect={onGiftSelect}
            />
          )
        })}
      </ResponsiveCardGrid>
    </div>
  )
}
