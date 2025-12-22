import type { EGO } from '@/types/EGOTypes'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { EGOCard } from './EGOCard'
import { getSinnerFromId } from '@/lib/utils'

interface EGOListProps {
  egos: EGO[]
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
  searchQuery: string
  onSelectEgo?: (ego: EGO) => void
  equippedIds?: Set<string>
}

export function EGOList({
  egos,
  selectedSinners,
  selectedKeywords,
  searchQuery,
  onSelectEgo,
  equippedIds,
}: EGOListProps) {
  const { keywordToValue } = useSearchMappings()

  // Filter EGOs based on selected sinners, keywords, and search query
  // Same filtering logic as IdentityList
  const filteredEGOs = egos.filter((ego) => {
    // Sinner filter
    if (selectedSinners.size > 0) {
      if (!selectedSinners.has(getSinnerFromId(ego.id))) {
        return false
      }
    }

    // Keyword filter - EGO must have ALL selected keywords
    if (selectedKeywords.size > 0) {
      const hasAllKeywords = Array.from(selectedKeywords).every((selectedKeyword) =>
        ego.skillKeywordList.includes(selectedKeyword)
      )
      if (!hasAllKeywords) {
        return false
      }
    }

    // Search filter - match name OR keyword
    if (searchQuery) {
      const lowerQuery = searchQuery.toLowerCase()

      // Check name match (partial, case-insensitive)
      const nameMatch = ego.name.toLowerCase().includes(lowerQuery)

      // Check keyword match (partial match on natural language, then lookup bracketed values)
      const keywordMatch = Array.from(keywordToValue.entries()).some(([naturalLang, bracketedValues]) => {
        if (naturalLang.includes(lowerQuery)) {
          return bracketedValues.some((bracketedValue) => ego.skillKeywordList.includes(bracketedValue))
        }
        return false
      })

      // Must match at least one category
      if (!nameMatch && !keywordMatch) {
        return false
      }
    }

    return true
  })

  // Sort: equipped EGOs first
  const sortedEGOs = equippedIds?.size
    ? [...filteredEGOs].sort((a, b) => {
        const aEquipped = equippedIds.has(a.id) ? 0 : 1
        const bEquipped = equippedIds.has(b.id) ? 0 : 1
        return aEquipped - bEquipped
      })
    : filteredEGOs

  return (
    <div className="bg-muted border border-border rounded-md p-6">
      {/* Responsive grid layout */}
      <div className="pt-4">
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8 gap-4 justify-items-center">
          {sortedEGOs.map((ego) => (
            <EGOCard
              key={ego.id}
              ego={ego}
              isSelected={equippedIds?.has(ego.id)}
              onSelect={onSelectEgo}
            />
          ))}
        </div>
      </div>
    </div>
  )
}
