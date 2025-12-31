import { useMemo } from 'react'
import type { EGO } from '@/types/EGOTypes'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { CARD_GRID } from '@/lib/constants'
import { sortByReleaseDate } from '@/lib/entitySort'
import { getSinnerFromId } from '@/lib/utils'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { EGOCardLink } from './EGOCardLink'

interface EGOListProps {
  egos: EGO[]
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
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
  searchQuery,
}: EGOListProps) {
  const { keywordToValue } = useSearchMappings()

  // Sort all EGOs once (stable order for CSS-based filtering)
  const sortedEGOs = useMemo(() => sortByReleaseDate(egos), [egos])

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
        if (!nameMatch && !keywordMatch) continue
      }

      ids.add(ego.id)
    }

    return ids
  }, [sortedEGOs, selectedSinners, selectedKeywords, searchQuery, keywordToValue])

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
