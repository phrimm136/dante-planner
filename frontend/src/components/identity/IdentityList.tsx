import type { Identity } from '@/types/IdentityTypes'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { IdentityCardLink } from './IdentityCardLink'
import { getSinnerFromId } from '@/lib/utils'

interface IdentityListProps {
  identities: Identity[]
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
  selectedAttributes: Set<string>
  selectedAtkTypes: Set<string>
  selectedRanks: Set<number>
  selectedSeasons: Set<number>
  selectedAssociations: Set<string>
  searchQuery: string
}

/**
 * IdentityList - Renders filtered list of identity cards
 *
 * Filter Logic:
 * - All filter types use AND between each other
 * - Sinner: OR logic (any selected sinner)
 * - Keyword: AND logic (must have ALL selected keywords)
 * - Attribute: OR logic (any selected attribute)
 * - Attack Type: OR logic (any selected attack type)
 * - Rank: OR logic (any selected rank)
 * - Season: OR logic (any selected season)
 * - Association: OR logic (any selected association)
 * - Search: OR logic (name OR keyword OR trait)
 */
export function IdentityList({
  identities,
  selectedSinners,
  selectedKeywords,
  selectedAttributes,
  selectedAtkTypes,
  selectedRanks,
  selectedSeasons,
  selectedAssociations,
  searchQuery,
}: IdentityListProps) {
  const { keywordToValue, unitKeywordToValue } = useSearchMappings()

  // Filter identities based on all filter criteria
  const filteredIdentities = identities.filter((identity) => {
    // Sinner filter - OR logic (match any selected sinner)
    if (selectedSinners.size > 0) {
      if (!selectedSinners.has(getSinnerFromId(identity.id))) {
        return false
      }
    }

    // Keyword filter - AND logic (identity must have ALL selected keywords)
    if (selectedKeywords.size > 0) {
      const hasAllKeywords = Array.from(selectedKeywords).every((selectedKeyword) =>
        identity.skillKeywordList.includes(selectedKeyword)
      )
      if (!hasAllKeywords) {
        return false
      }
    }

    // Attribute filter - OR logic (identity has ANY selected attribute)
    if (selectedAttributes.size > 0) {
      const hasAnyAttribute = identity.attributeTypes.some((attr) =>
        selectedAttributes.has(attr)
      )
      if (!hasAnyAttribute) {
        return false
      }
    }

    // Attack type filter - OR logic (identity has ANY selected attack type)
    if (selectedAtkTypes.size > 0) {
      const hasAnyAtkType = identity.atkTypes.some((atkType) =>
        selectedAtkTypes.has(atkType)
      )
      if (!hasAnyAtkType) {
        return false
      }
    }

    // Rank filter - OR logic (identity rank matches ANY selected rank)
    if (selectedRanks.size > 0) {
      if (!selectedRanks.has(identity.rank)) {
        return false
      }
    }

    // Season filter - OR logic (identity season matches ANY selected season)
    if (selectedSeasons.size > 0) {
      if (!selectedSeasons.has(identity.season)) {
        return false
      }
    }

    // Association filter - OR logic (identity has ANY selected association)
    if (selectedAssociations.size > 0) {
      const hasAnyAssociation = identity.associationList.some((assoc) =>
        selectedAssociations.has(assoc)
      )
      if (!hasAnyAssociation) {
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
          return bracketedValues.some((bracketedValue) => identity.skillKeywordList.includes(bracketedValue))
        }
        return false
      })

      // Check unit keyword match (partial match on natural language, then lookup internal codes)
      const unitKeywordMatch = Array.from(unitKeywordToValue.entries()).some(([naturalLang, internalCodes]) => {
        if (naturalLang.includes(lowerQuery)) {
          return internalCodes.some((internalCode) => identity.unitKeywordList.includes(internalCode))
        }
        return false
      })

      // Must match at least one category
      if (!nameMatch && !keywordMatch && !unitKeywordMatch) {
        return false
      }
    }

    return true
  })

  if (filteredIdentities.length === 0) {
    return (
      <div className="bg-muted border border-border rounded-md p-6">
        <div className="text-center text-muted-foreground py-8">
          No Identities match your current filters and search criteria
        </div>
      </div>
    )
  }

  return (
    <div className="bg-muted border border-border rounded-md p-6">
      {/* Responsive grid layout with padding for sinner icons/bg */}
      <div className="pt-4">
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8 gap-4 justify-items-center">
          {filteredIdentities.map((identity) => (
            <IdentityCardLink key={identity.id} identity={identity} />
          ))}
        </div>
      </div>
    </div>
  )
}
