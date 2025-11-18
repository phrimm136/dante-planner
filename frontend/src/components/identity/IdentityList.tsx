import { useIdentityData } from '@/hooks/useIdentityData'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { IdentityCard } from './IdentityCard'

interface IdentityListProps {
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
  searchQuery: string
}

export function IdentityList({ selectedSinners, selectedKeywords, searchQuery }: IdentityListProps) {
  const identities = useIdentityData()
  const { keywordToValue, traitToValue } = useSearchMappings()

  // Filter identities based on selected sinners, keywords, and search query
  // If no filters selected, show all identities
  // Sinner filter: must match selected sinner (AND logic)
  // Keyword filter: must have ALL selected keywords (AND logic)
  // Search filter: must match name OR keyword OR trait (OR logic within search)
  // All filters: must pass sinner AND keyword AND search (AND between filter types)
  const filteredIdentities = identities.filter((identity) => {
    // Sinner filter
    if (selectedSinners.size > 0) {
      if (!selectedSinners.has(identity.sinner)) {
        return false
      }
    }

    // Keyword filter - identity must have ALL selected keywords
    if (selectedKeywords.size > 0) {
      const hasAllKeywords = Array.from(selectedKeywords).every((selectedKeyword) =>
        identity.keywords.includes(selectedKeyword)
      )
      if (!hasAllKeywords) {
        return false
      }
    }

    // Search filter - match name OR keyword OR trait
    if (searchQuery) {
      const lowerQuery = searchQuery.toLowerCase()

      // Check name match (partial, case-insensitive)
      const nameMatch = identity.name.toLowerCase().includes(lowerQuery)

      // Check keyword match (partial match on natural language, then lookup bracketed values)
      const keywordMatch = Array.from(keywordToValue.entries()).some(([naturalLang, bracketedValues]) => {
        if (naturalLang.includes(lowerQuery)) {
          return bracketedValues.some((bracketedValue) => identity.keywords.includes(bracketedValue))
        }
        return false
      })

      // Check trait match (partial match on natural language, then lookup bracketed values)
      const traitMatch = Array.from(traitToValue.entries()).some(([naturalLang, bracketedValues]) => {
        if (naturalLang.includes(lowerQuery)) {
          return bracketedValues.some((bracketedValue) => identity.traits.includes(bracketedValue))
        }
        return false
      })

      // Must match at least one category
      if (!nameMatch && !keywordMatch && !traitMatch) {
        return false
      }
    }

    return true
  })

  return (
    <div className="bg-muted border border-border rounded-md p-6">
      {/* Responsive grid layout with padding for sinner icons/bg */}
      <div className="pt-4">
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8 gap-4 justify-items-center">
          {filteredIdentities.map((identity) => (
            <IdentityCard key={identity.id} identity={identity} />
          ))}
        </div>
      </div>
    </div>
  )
}
