import type { EGO } from '@/types/EGOTypes'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { EGOCardLink } from './EGOCardLink'
import { getSinnerFromId } from '@/lib/utils'

interface EGOListProps {
  egos: EGO[]
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
  searchQuery: string
}

export function EGOList({
  egos,
  selectedSinners,
  selectedKeywords,
  searchQuery,
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

  if (filteredEGOs.length === 0) {
    return (
      <div className="bg-muted border border-border rounded-md p-6">
        <div className="text-center text-muted-foreground py-8">
          No EGOs match your current filters and search criteria
        </div>
      </div>
    )
  }

  return (
    <div className="bg-muted border border-border rounded-md p-6">
      {/* Responsive grid layout */}
      <div className="pt-4">
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8 gap-4 justify-items-center">
          {filteredEGOs.map((ego) => (
            <EGOCardLink key={ego.id} ego={ego} />
          ))}
        </div>
      </div>
    </div>
  )
}
