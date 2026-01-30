import { useMemo, useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'

import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EGOGiftAttributeType, EGOGiftDifficulty, EGOGiftTier } from '@/lib/constants'
import { CARD_GRID } from '@/lib/constants'
import { useSearchMappingsDeferred } from '@/hooks/useSearchMappings'
import { useEGOGiftListI18nDeferred } from '@/hooks/useEGOGiftListData'
import { sortEGOGifts } from '@/lib/egoGiftSort'
import {
  matchesKeywordFilter,
  matchesDifficultyFilter,
  matchesTierFilter,
  matchesThemePackFilter,
  matchesAttributeTypeFilter,
} from '@/lib/egoGiftFilter'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { ScaledCardWrapper } from '@/components/common/ScaledCardWrapper'
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
  const { t } = useTranslation('database')
  // Non-suspending: returns empty mappings while loading, search won't match until loaded
  const { keywordToValue } = useSearchMappingsDeferred()
  // Non-suspending: returns empty object while loading, name search won't match until loaded
  const giftNames = useEGOGiftListI18nDeferred()

  // Sort all gifts once (stable order for CSS-based filtering)
  // Default sort: tier-first (higher tier first, then by keyword)
  const sortedGifts = useMemo(() => sortEGOGifts(gifts, 'tier-first'), [gifts])

  // Progressive rendering: start with 10 cards, add more incrementally
  const [displayCount, setDisplayCount] = useState(10)

  // Reset display count when gifts change (new data loaded)
  useEffect(() => {
    setDisplayCount(10)
  }, [sortedGifts])

  // Progressively render more cards (10 per frame)
  useEffect(() => {
    if (displayCount < sortedGifts.length) {
      const rafId = requestAnimationFrame(() => {
        setDisplayCount((prev) => Math.min(prev + 10, sortedGifts.length))
      })
      return () => cancelAnimationFrame(rafId)
    }
  }, [displayCount, sortedGifts.length])

  // Create Set of visible gift IDs based on filters
  // This is fast O(n) computation, much cheaper than React reconciliation
  const visibleIds = useMemo(() => {
    const ids = new Set<string>()

    // Cache array conversion before loop to avoid O(N×M) allocations
    const keywordEntries = Array.from(keywordToValue.entries())

    for (const gift of sortedGifts) {
      // Apply all filters using extracted utility functions
      // Each filter: OR logic within, AND logic across filter types
      if (!matchesKeywordFilter(gift.keyword, selectedKeywords)) continue
      if (!matchesDifficultyFilter(gift, selectedDifficulties)) continue
      if (!matchesTierFilter(gift.tag, selectedTiers)) continue
      if (!matchesThemePackFilter(gift.themePack, selectedThemePacks)) continue
      if (!matchesAttributeTypeFilter(gift.attributeType, selectedAttributeTypes)) continue

      // Search filter - match name OR keyword (both deferred, no suspension)
      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()

        // Check name match (partial, case-insensitive)
        const giftName = giftNames[gift.id] ?? ''
        const nameMatch = giftName.toLowerCase().includes(lowerQuery)

        // Check keyword match (partial match on natural language, then lookup PascalCase values)
        const keywordMatch = keywordEntries.some(([naturalLang, pascalValues]) => {
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
    giftNames,
  ])

  if (visibleIds.size === 0) {
    return (
      <div className="bg-muted border border-border rounded-md p-6">
        <div className="text-center text-muted-foreground py-8">
          {t('egoGift.emptyState')}
        </div>
      </div>
    )
  }

  return (
    <div className="bg-muted border border-border rounded-md p-6">
      <ResponsiveCardGrid
        cardWidth={CARD_GRID.WIDTH.EGO_GIFT}
        cardHeight={CARD_GRID.HEIGHT.EGO_GIFT}
        mobileScale={0.8}
      >
        {sortedGifts.slice(0, displayCount).map((gift) => (
          <ScaledCardWrapper
            key={gift.id}
            mobileScale={0.8}
            cardWidth={CARD_GRID.WIDTH.EGO_GIFT}
            cardHeight={CARD_GRID.HEIGHT.EGO_GIFT}
            className={visibleIds.has(gift.id) ? '' : 'hidden'}
          >
            <EGOGiftCardLink gift={gift} />
          </ScaledCardWrapper>
        ))}
      </ResponsiveCardGrid>
    </div>
  )
}
