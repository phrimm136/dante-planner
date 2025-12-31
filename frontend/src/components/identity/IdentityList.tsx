import { useMemo } from 'react'
import type { Identity } from '@/types/IdentityTypes'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { CARD_GRID } from '@/lib/constants'
import { sortByReleaseDate } from '@/lib/entitySort'
import { getSinnerFromId } from '@/lib/utils'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { IdentityCardLink } from './IdentityCardLink'

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
 * IdentityList - Renders list of identity cards with CSS-based filtering
 *
 * All cards are rendered once, visibility is toggled via CSS class.
 * This eliminates React reconciliation on filter changes.
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

  // Sort all identities once (stable order for CSS-based filtering)
  const sortedIdentities = useMemo(
    () => sortByReleaseDate(identities),
    [identities]
  )

  // Create Set of visible identity IDs based on filters
  // This is fast O(n) computation, much cheaper than React reconciliation
  const visibleIds = useMemo(() => {
    const ids = new Set<string>()

    for (const identity of sortedIdentities) {
      // Sinner filter - OR logic (match any selected sinner)
      if (selectedSinners.size > 0) {
        if (!selectedSinners.has(getSinnerFromId(identity.id))) continue
      }

      // Keyword filter - AND logic (identity must have ALL selected keywords)
      if (selectedKeywords.size > 0) {
        const hasAllKeywords = Array.from(selectedKeywords).every((selectedKeyword) =>
          identity.skillKeywordList.includes(selectedKeyword)
        )
        if (!hasAllKeywords) continue
      }

      // Attribute filter - OR logic (identity has ANY selected attribute)
      if (selectedAttributes.size > 0) {
        const hasAnyAttribute = identity.attributeTypes.some((attr) =>
          selectedAttributes.has(attr)
        )
        if (!hasAnyAttribute) continue
      }

      // Attack type filter - OR logic (identity has ANY selected attack type)
      if (selectedAtkTypes.size > 0) {
        const hasAnyAtkType = identity.atkTypes.some((atkType) =>
          selectedAtkTypes.has(atkType)
        )
        if (!hasAnyAtkType) continue
      }

      // Rank filter - OR logic (identity rank matches ANY selected rank)
      if (selectedRanks.size > 0) {
        if (!selectedRanks.has(identity.rank)) continue
      }

      // Season filter - OR logic (identity season matches ANY selected season)
      if (selectedSeasons.size > 0) {
        if (!selectedSeasons.has(identity.season)) continue
      }

      // Association filter - OR logic (identity has ANY selected association)
      if (selectedAssociations.size > 0) {
        const hasAnyAssociation = identity.associationList.some((assoc) =>
          selectedAssociations.has(assoc)
        )
        if (!hasAnyAssociation) continue
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
        if (!nameMatch && !keywordMatch && !unitKeywordMatch) continue
      }

      ids.add(identity.id)
    }

    return ids
  }, [
    sortedIdentities,
    selectedSinners,
    selectedKeywords,
    selectedAttributes,
    selectedAtkTypes,
    selectedRanks,
    selectedSeasons,
    selectedAssociations,
    searchQuery,
    keywordToValue,
    unitKeywordToValue,
  ])

  if (visibleIds.size === 0) {
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
        <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.IDENTITY}>
          {sortedIdentities.map((identity) => (
            <div
              key={identity.id}
              className={visibleIds.has(identity.id) ? '' : 'hidden'}
            >
              <IdentityCardLink identity={identity} />
            </div>
          ))}
        </ResponsiveCardGrid>
      </div>
    </div>
  )
}
