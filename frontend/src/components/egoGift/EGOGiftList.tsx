import { useMemo } from 'react'
import type { EGOGift } from '@/types/EGOGiftTypes'
import type { SortMode } from '@/components/common/Sorter'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { sortEGOGifts } from '@/lib/egoGiftSort'
import { EGOGiftCard } from './EGOGiftCard'

interface EGOGiftListProps {
  gifts: EGOGift[]
  selectedKeywords: Set<string>
  searchQuery: string
  sortMode: SortMode
}

export function EGOGiftList({ gifts, selectedKeywords, searchQuery, sortMode }: EGOGiftListProps) {
  const { keywordToValue } = useSearchMappings()

  // Filter and sort gifts
  const displayedGifts = useMemo(() => {
    // Filter gifts based on keywords and search query
    const filtered = gifts.filter((gift) => {
      // Keyword filter - gift must have ANY selected keyword (OR logic)
      if (selectedKeywords.size > 0) {
        const hasAnyKeyword = Array.from(selectedKeywords).some((selectedKeyword) =>
          gift.keywords.includes(selectedKeyword)
        )
        if (!hasAnyKeyword) {
          return false
        }
      }

      // Search filter - match name OR keyword OR themePack
      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()

        // Check name match (partial, case-insensitive)
        const nameMatch = gift.name.toLowerCase().includes(lowerQuery)

        // Check keyword match (partial match on natural language, then lookup PascalCase values)
        const keywordMatch = Array.from(keywordToValue.entries()).some(([naturalLang, pascalValues]) => {
          if (naturalLang.includes(lowerQuery)) {
            return pascalValues.some((pascalValue) => gift.keywords.includes(pascalValue))
          }
          return false
        })

        // Check themePack match (partial, case-insensitive)
        const themePackMatch = gift.themePack.some((theme) => theme.toLowerCase().includes(lowerQuery))

        // Must match at least one category
        if (!nameMatch && !keywordMatch && !themePackMatch) {
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
        <div className="text-center text-gray-500 py-8">
          No EGO Gifts match your current filters and search criteria
        </div>
      </div>
    )
  }

  return (
    <div className="bg-muted border border-border rounded-md p-6">
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
        {displayedGifts.map((gift) => (
          <EGOGiftCard key={gift.id} gift={gift} />
        ))}
      </div>
    </div>
  )
}
