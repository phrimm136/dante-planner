import { useMemo, useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'

import type { BuffType } from '@/lib/constants'
import { CARD_GRID } from '@/lib/constants'
import { useKeywordListI18nDeferred } from '@/hooks/useKeywordListData'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { ScaledCardWrapper } from '@/components/common/ScaledCardWrapper'
import { KeywordCardLink } from './KeywordCardLink'

interface KeywordListItem {
  id: string
  iconId: string | null
  buffType: string
  identities: string[]
  egos: string[]
  egoGifts: string[]
}

interface KeywordListProps {
  keywords: KeywordListItem[]
  selectedBuffTypes: Set<BuffType>
  selectedIdentities: Set<string>
  selectedEgos: Set<string>
  selectedEgoGifts: Set<string>
  searchQuery: string
}

/**
 * KeywordList - Renders keyword cards with CSS-based filtering
 *
 * All cards are rendered once, visibility is toggled via CSS class.
 * This eliminates React reconciliation on filter changes.
 *
 * Pattern Source: EGOGiftList.tsx
 *
 * Filter Logic:
 * - All filter types use AND between each other
 * - BuffType: OR logic (any selected buffType)
 * - Identity/EGO/EGOGift: OR logic within each, AND across entity types
 * - Search: case-insensitive substring on localized name
 */
export function KeywordList({
  keywords,
  selectedBuffTypes,
  selectedIdentities,
  selectedEgos,
  selectedEgoGifts,
  searchQuery,
}: KeywordListProps) {
  const { t } = useTranslation('database')
  const keywordNames = useKeywordListI18nDeferred()

  // Progressive rendering: start with 50 cards, add 50 per frame
  const [displayCount, setDisplayCount] = useState(50)

  // Reset display count when keywords change
  useEffect(() => {
    setDisplayCount(50)
  }, [keywords])

  // Progressively render more cards
  useEffect(() => {
    if (displayCount < keywords.length) {
      const rafId = requestAnimationFrame(() => {
        setDisplayCount((prev) => Math.min(prev + 50, keywords.length))
      })
      return () => cancelAnimationFrame(rafId)
    }
  }, [displayCount, keywords.length])

  // Create Set of visible keyword IDs based on filters
  const visibleIds = useMemo(() => {
    const ids = new Set<string>()

    for (const keyword of keywords) {
      if (selectedBuffTypes.size > 0 && !selectedBuffTypes.has(keyword.buffType as BuffType)) {
        continue
      }

      if (selectedIdentities.size > 0) {
        if (!keyword.identities.some((id) => selectedIdentities.has(id))) continue
      }

      if (selectedEgos.size > 0) {
        if (!keyword.egos.some((id) => selectedEgos.has(id))) continue
      }

      if (selectedEgoGifts.size > 0) {
        if (!keyword.egoGifts.some((id) => selectedEgoGifts.has(id))) continue
      }

      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()
        const name = keywordNames[keyword.id]?.name ?? ''
        if (!name.toLowerCase().includes(lowerQuery)) continue
      }

      ids.add(keyword.id)
    }

    return ids
  }, [
    keywords,
    selectedBuffTypes,
    selectedIdentities,
    selectedEgos,
    selectedEgoGifts,
    searchQuery,
    keywordNames,
  ])

  if (visibleIds.size === 0) {
    return (
      <div className="bg-muted border border-border rounded-md p-6">
        <div className="text-center text-muted-foreground py-8">
          {t('keyword.emptyState')}
        </div>
      </div>
    )
  }

  return (
    <div className="bg-muted border border-border rounded-md p-6">
      <ResponsiveCardGrid
        cardWidth={CARD_GRID.WIDTH.KEYWORD}
        mobileScale={0.8}
      >
        {keywords.slice(0, displayCount).map((keyword) => (
          <ScaledCardWrapper
            key={keyword.id}
            mobileScale={0.8}
            cardWidth={CARD_GRID.WIDTH.KEYWORD}
            cardHeight={CARD_GRID.HEIGHT.KEYWORD}
            className={visibleIds.has(keyword.id) ? '' : 'hidden'}
          >
            <KeywordCardLink
              id={keyword.id}
              iconId={keyword.iconId}
              buffType={keyword.buffType}
            />
          </ScaledCardWrapper>
        ))}
      </ResponsiveCardGrid>
    </div>
  )
}
