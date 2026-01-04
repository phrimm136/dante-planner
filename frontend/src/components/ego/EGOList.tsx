import { useMemo } from 'react'
import type { EGOListItem } from '@/types/EGOTypes'
import { useSearchMappingsDeferred } from '@/hooks/useSearchMappings'
import { useEGOListI18nDeferred } from '@/hooks/useEGOListData'
import type { Season } from '@/lib/constants'
import { CARD_GRID } from '@/lib/constants'
import { sortEGOByDate } from '@/lib/entitySort'
import { getSinnerFromId } from '@/lib/utils'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { EGOCardLink } from './EGOCardLink'

interface EGOListProps {
  egos: EGOListItem[]
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
  selectedAttributes: Set<string>
  selectedAtkTypes: Set<string>
  selectedEGOTypes: Set<string>
  selectedSeasons: Set<Season>
  searchQuery: string
}

/**
 * EGOList - Renders list of EGO cards with CSS-based filtering
 *
 * All cards are rendered once, visibility is toggled via CSS class.
 * This eliminates React reconciliation on filter changes.
 */
export function EGOList({
  egos,
  selectedSinners,
  selectedKeywords,
  selectedAttributes,
  selectedAtkTypes,
  selectedEGOTypes,
  selectedSeasons,
  searchQuery,
}: EGOListProps) {
  // Non-suspending: returns empty mappings while loading, search won't match until loaded
  const { keywordToValue } = useSearchMappingsDeferred()
  // Non-suspending: returns empty object while loading, name search won't match until loaded
  const egoNames = useEGOListI18nDeferred()

  // Sort all EGOs once (stable order for CSS-based filtering)
  const sortedEGOs = useMemo(() => sortEGOByDate(egos), [egos])

  // Create Set of visible EGO IDs based on filters
  // This is fast O(n) computation, much cheaper than React reconciliation
  const visibleIds = useMemo(() => {
    const ids = new Set<string>()

    for (const ego of sortedEGOs) {
      // Sinner filter
      if (selectedSinners.size > 0) {
        if (!selectedSinners.has(getSinnerFromId(ego.id))) continue
      }

      // Keyword filter - EGO must have ALL selected keywords
      if (selectedKeywords.size > 0) {
        const hasAllKeywords = Array.from(selectedKeywords).every((selectedKeyword) =>
          ego.skillKeywordList.includes(selectedKeyword)
        )
        if (!hasAllKeywords) continue
      }

      // Skill attribute filter - EGO must have at least one selected attribute
      if (selectedAttributes.size > 0) {
        const hasAttribute = ego.attributeType.some((attr) => selectedAttributes.has(attr))
        if (!hasAttribute) continue
      }

      // Attack type filter - EGO must have at least one selected attack type
      if (selectedAtkTypes.size > 0) {
        const hasAtkType = ego.atkType.some((atkType) => selectedAtkTypes.has(atkType))
        if (!hasAtkType) continue
      }

      // EGO type filter - EGO must match one of selected types
      if (selectedEGOTypes.size > 0) {
        if (!selectedEGOTypes.has(ego.egoType)) continue
      }

      // Season filter - EGO must match one of selected seasons
      if (selectedSeasons.size > 0) {
        if (!selectedSeasons.has(ego.season)) continue
      }

      // Search filter - match name OR keyword (both deferred, no suspension)
      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()

        // Check name match (partial, case-insensitive)
        const egoName = egoNames[ego.id] ?? ''
        const nameMatch = egoName.toLowerCase().includes(lowerQuery)

        // Check keyword match (partial match on natural language, then lookup bracketed values)
        const keywordMatch = Array.from(keywordToValue.entries()).some(([naturalLang, bracketedValues]) => {
          if (naturalLang.includes(lowerQuery)) {
            return bracketedValues.some((bracketedValue) => ego.skillKeywordList.includes(bracketedValue))
          }
          return false
        })

        // Must match at least one
        if (!nameMatch && !keywordMatch) continue
      }

      ids.add(ego.id)
    }

    return ids
  }, [sortedEGOs, selectedSinners, selectedKeywords, selectedAttributes, selectedAtkTypes, selectedEGOTypes, selectedSeasons, searchQuery, keywordToValue, egoNames])

  if (visibleIds.size === 0) {
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
        <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.EGO}>
          {sortedEGOs.map((ego) => (
            <div
              key={ego.id}
              className={visibleIds.has(ego.id) ? '' : 'hidden'}
            >
              <EGOCardLink ego={ego} />
            </div>
          ))}
        </ResponsiveCardGrid>
      </div>
    </div>
  )
}
