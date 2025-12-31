import { useMemo } from 'react'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { SortMode } from '@/components/common/Sorter'
import { CARD_GRID } from '@/lib/constants'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { sortEGOGifts } from '@/lib/egoGiftSort'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { EGOGiftCardLink } from './EGOGiftCardLink'

interface EGOGiftListProps {
  gifts: EGOGiftListItem[]
  selectedKeywords: Set<string>
  searchQuery: string
  sortMode: SortMode
}

export function EGOGiftList({ gifts, selectedKeywords, searchQuery, sortMode }: EGOGiftListProps) {
  const { keywordToValue } = useSearchMappings()

  // Filter and sort gifts
  const displayedGifts = useMemo(() => {
    // Filter gifts based on keyword and search query
    const filtered = gifts.filter((gift) => {
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
    return sortEGOGifts(filtered, sortMode)
  }, [gifts, selectedKeywords, searchQuery, sortMode, keywordToValue])

  if (displayedGifts.length === 0) {
    return (
      <div className="bg-muted border border-border rounded-md p-6">
        <div className="text-center text-muted-foreground py-8">
          No EGO Gifts match your current filters and search criteria
        </div>
      </div>
    )
  }

  return (
    <div className="bg-muted border border-border rounded-md p-6">
      <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.EGO_GIFT}>
        {displayedGifts.map((gift) => (
          <EGOGiftCardLink key={gift.id} gift={gift} />
        ))}
      </ResponsiveCardGrid>
    </div>
  )
}
