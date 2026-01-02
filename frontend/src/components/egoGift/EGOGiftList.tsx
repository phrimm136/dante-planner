import { useMemo } from 'react'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EGOGiftDifficulty, EGOGiftTier } from '@/lib/constants'
import { CARD_GRID } from '@/lib/constants'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { sortEGOGifts } from '@/lib/egoGiftSort'
import {
  matchesKeywordFilter,
  matchesDifficultyFilter,
  matchesTierFilter,
  matchesThemePackFilter,
  matchesAttributeTypeFilter,
} from '@/lib/egoGiftFilter'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { EGOGiftCardLink } from './EGOGiftCardLink'

interface EGOGiftListProps {
  gifts: EGOGiftListItem[]
  selectedKeywords: Set<string>
  selectedDifficulties: Set<EGOGiftDifficulty>
  selectedTiers: Set<EGOGiftTier>
  selectedThemePacks: Set<string>
  selectedAttributeTypes: Set<EGOGiftAttributeType>
  searchQuery: string
}

/**
 * EGOGiftList - Renders list of EGO Gift cards with CSS-based filtering
 *
 * All cards are rendered once, visibility is toggled via CSS class.
 * This eliminates React reconciliation on filter changes.
 *
 * Filter Logic:
 * - All filter types use AND between each other
 * - Keyword: OR logic (any selected keyword)
 * - Difficulty: OR logic (any selected difficulty)
 * - Tier: OR logic (any selected tier)
 * - Theme Pack: OR logic (any selected theme pack)
 * - Attribute Type: OR logic (any selected attribute type)
 * - Search: OR logic (name OR keyword)
 */
export function EGOGiftList({
  gifts,
  selectedKeywords,
  selectedDifficulties,
  selectedTiers,
  selectedThemePacks,
  selectedAttributeTypes,
  searchQuery,
}: EGOGiftListProps) {
  const { keywordToValue } = useSearchMappings()

  // Sort all gifts once (stable order for CSS-based filtering)
  // Default sort: tier-first (higher tier first, then by keyword)
  const sortedGifts = useMemo(() => sortEGOGifts(gifts, 'tier-first'), [gifts])

  // Create Set of visible gift IDs based on filters
  // This is fast O(n) computation, much cheaper than React reconciliation
  const visibleIds = useMemo(() => {
    const ids = new Set<string>()

    for (const gift of sortedGifts) {
      // Apply all filters using extracted utility functions
      // Each filter: OR logic within, AND logic across filter types
      if (!matchesKeywordFilter(gift.keyword, selectedKeywords)) continue
      if (!matchesDifficultyFilter(gift, selectedDifficulties)) continue
      if (!matchesTierFilter(gift.tag, selectedTiers)) continue
      if (!matchesThemePackFilter(gift.themePack, selectedThemePacks)) continue
      if (!matchesAttributeTypeFilter(gift.attributeType, selectedAttributeTypes)) continue

      // Search filter - match name OR keyword
      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()

        // Check name match (partial, case-insensitive)
        const nameMatch = gift.name.toLowerCase().includes(lowerQuery)

        // Check keyword match (partial match on natural language, then lookup PascalCase values)
        const keywordMatch = Array.from(keywordToValue.entries()).some(([naturalLang, pascalValues]) => {
          if (naturalLang.includes(lowerQuery)) {
            return gift.keyword && pascalValues.includes(gift.keyword)
          }
          return false
        })

        // Must match at least one
        if (!nameMatch && !keywordMatch) continue
      }

      ids.add(gift.id)
    }

    return ids
  }, [
    sortedGifts,
    selectedKeywords,
    selectedDifficulties,
    selectedTiers,
    selectedThemePacks,
    selectedAttributeTypes,
    searchQuery,
    keywordToValue,
  ])

  if (visibleIds.size === 0) {
    return (
      <div className="bg-muted border border-border rounded-md p-6">
        <div className="text-center text-muted-foreground py-8">
          No EGO Gifts match your current filters and search criteria
        </div>
      </div>
    )
  }

  return (
    <div className="bg-muted border border-border rounded-md p-6">
      <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.EGO_GIFT}>
        {sortedGifts.map((gift) => (
          <div
            key={gift.id}
            className={visibleIds.has(gift.id) ? '' : 'hidden'}
          >
            <EGOGiftCardLink gift={gift} />
          </div>
        ))}
      </ResponsiveCardGrid>
    </div>
  )
}
