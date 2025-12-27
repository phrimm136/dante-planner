import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { SortMode } from '@/components/common/Sorter'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { sortEGOGifts } from '@/lib/egoGiftSort'
import { EgoGiftMiniCard } from './EgoGiftMiniCard'
interface EGOGiftSelectionListProps {
  gifts: EGOGiftListItem[]
  giftIdFilter?: number[]
  selectedKeywords: Set<string>
  searchQuery: string
  sortMode: SortMode
  selectedGiftIds: Set<string>
  maxSelectable: number
  onGiftSelect: (giftId: string) => void
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
}: EGOGiftSelectionListProps) {
  const { keywordToValue } = useSearchMappings()
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
      const nameMatch = gift.name.toLowerCase().includes(lowerQuery)
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
      <div className="grid grid-cols-[repeat(auto-fill,minmax(96px,1fr))] gap-4">
        {displayedGifts.map((gift) => {
          const isSelected = selectedGiftIds.has(gift.id)
          const canSelect = isSelected || selectedGiftIds.size < maxSelectable
          const tier = gift.tag.find(t => t.startsWith('TIER_'))!.replace('TIER_', '')
          return (
            <EgoGiftMiniCard
              key={gift.id}
              giftId={gift.id}
              giftName={gift.name}
              attributeType={gift.attributeType}
              tier={tier}
              keyword={gift.keyword}
              isSelected={isSelected}
              isSelectable={canSelect}
              onSelect={onGiftSelect}
            />
          )
        })}
      </div>
    </div>
  )
}
