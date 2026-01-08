import { useMemo } from 'react'
import type { IdentityListItem } from '@/types/IdentityTypes'
import { useSearchMappingsDeferred } from '@/hooks/useSearchMappings'
import { useIdentityListI18nDeferred } from '@/hooks/useIdentityListData'
import { CARD_GRID, type Season } from '@/lib/constants'
import { sortByReleaseDate } from '@/lib/entitySort'
import { getSinnerFromId } from '@/lib/utils'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { IdentityCardLink } from './IdentityCardLink'

interface IdentityListProps {
  identities: IdentityListItem[]
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
  selectedAttributes: Set<string>
  selectedAtkTypes: Set<string>
  selectedRaritys: Set<number>
  selectedSeasons: Set<Season>
  selectedUnitKeywords: Set<string>
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
  selectedRaritys,
  selectedSeasons,
  selectedUnitKeywords,
  searchQuery,
}: IdentityListProps) {
  // Non-suspending: returns empty mappings while loading, search won't match until loaded
  const { keywordToValue, unitKeywordToValue } = useSearchMappingsDeferred()
  // Non-suspending: returns empty object while loading, name search won't match until loaded
  const identityNames = useIdentityListI18nDeferred()

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

      // Rarity filter - OR logic (identity rarity matches ANY selected rarity)
      if (selectedRaritys.size > 0) {
        if (!selectedRaritys.has(identity.rank)) continue
      }

      // Season filter - OR logic (identity season matches ANY selected season)
      if (selectedSeasons.size > 0) {
        if (!selectedSeasons.has(identity.season)) continue
      }

      // Unit keyword filter - OR logic (identity has ANY selected unit keyword in unitKeywordList)
      if (selectedUnitKeywords.size > 0) {
        const hasAnyUnitKeyword = identity.unitKeywordList.some((keyword) =>
          selectedUnitKeywords.has(keyword)
        )
        if (!hasAnyUnitKeyword) continue
      }

      // Search filter - match name OR keyword OR trait (both deferred, no suspension)
      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()

        // Check name match (partial, case-insensitive)
        const identityName = identityNames[identity.id] ?? ''
        const nameMatch = identityName.toLowerCase().includes(lowerQuery)

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
    selectedRaritys,
    selectedSeasons,
    selectedUnitKeywords,
    searchQuery,
    keywordToValue,
    unitKeywordToValue,
    identityNames,
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
