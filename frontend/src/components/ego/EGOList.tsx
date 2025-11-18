import { useEGOData } from '@/hooks/useEGOData'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { EGOCard } from './EGOCard'
import { parseBracketNotation } from '@/lib/identityUtils'

interface EGOListProps {
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
  searchQuery: string
}

export function EGOList({ selectedSinners, selectedKeywords, searchQuery }: EGOListProps) {
  const egos = useEGOData()
  const { keywordToValue } = useSearchMappings()

  // Filter EGOs based on selected sinners, keywords, and search query
  // Same filtering logic as IdentityList
  const filteredEGOs = egos.filter((ego) => {
    // Sinner filter
    if (selectedSinners.size > 0) {
      const sinnerName = parseBracketNotation(ego.sinner)
      if (!selectedSinners.has(sinnerName)) {
        return false
      }
    }

    // Keyword filter - EGO must have ALL selected keywords
    if (selectedKeywords.size > 0) {
      const egoKeywords = ego.keywords.map((keyword) => parseBracketNotation(keyword))
      const hasAllKeywords = Array.from(selectedKeywords).every((selectedKeyword) =>
        egoKeywords.includes(selectedKeyword)
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
          return bracketedValues.some((bracketedValue) => ego.keywords.includes(bracketedValue))
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

  return (
    <div className="bg-muted border border-border rounded-md p-6">
      {/* Responsive grid layout */}
      <div className="pt-4">
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8 gap-4 justify-items-center">
          {filteredEGOs.map((ego) => (
            <EGOCard key={ego.id} ego={ego} />
          ))}
        </div>
      </div>
    </div>
  )
}
