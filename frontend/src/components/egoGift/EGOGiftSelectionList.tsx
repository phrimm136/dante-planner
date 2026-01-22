import { useMemo, useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'

import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EnhancementLevel } from '@/lib/constants'
import { CARD_GRID } from '@/lib/constants'
import { useSearchMappingsDeferred } from '@/hooks/useSearchMappings'
import { buildSelectionLookup } from '@/lib/egoGiftEncoding'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { EGOGiftSelectableCard } from './EGOGiftSelectableCard'
import { EGOGiftObservationCard } from './EGOGiftObservationCard'
import { EGOGiftCard } from './EGOGiftCard'

interface EGOGiftSelectionListProps {
  gifts: EGOGiftListItem[]
  selectedKeywords: Set<string>
  searchQuery: string
  selectedGiftIds: Set<string>
  maxSelectable: number
  onGiftSelect?: (giftId: string) => void
  enableEnhancementSelection?: boolean
  onEnhancementSelect?: (giftId: string, enhancement: EnhancementLevel) => void
}

/**
 * EGO gift selection list - CSS-based filtering with progressive rendering
 * Renders 10 cards initially, then 10 more per frame via requestAnimationFrame
 */
export function EGOGiftSelectionList({
  gifts,
  selectedKeywords,
  searchQuery,
  selectedGiftIds,
  maxSelectable: _maxSelectable,
  onGiftSelect,
  enableEnhancementSelection = false,
  onEnhancementSelect,
}: EGOGiftSelectionListProps) {
  const { t } = useTranslation('database')
  const { keywordToValue } = useSearchMappingsDeferred()

  // Progressive rendering: start with 10 cards, add more incrementally
  const [displayCount, setDisplayCount] = useState(10)

  // Reset display count when gifts change
  useEffect(() => {
    setDisplayCount(10)
  }, [gifts])

  // Progressively render more cards (10 per frame)
  useEffect(() => {
    if (displayCount < gifts.length) {
      const rafId = requestAnimationFrame(() => {
        setDisplayCount((prev) => Math.min(prev + 10, gifts.length))
      })
      return () => cancelAnimationFrame(rafId)
    }
  }, [displayCount, gifts.length])

  const visibleIds = useMemo(() => {
    const ids = new Set<string>()
    for (const gift of gifts) {
      if (selectedKeywords.size > 0) {
        const giftKeyword = gift.keyword ?? 'None'
        if (!selectedKeywords.has(giftKeyword)) continue
      }
      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()
        const nameMatch = gift.name?.toLowerCase().includes(lowerQuery)
        const giftKeyword = gift.keyword ?? 'None'
        const keywordMatch = Array.from(keywordToValue.entries()).some(([naturalLang, pascalValues]) => {
          if (naturalLang.includes(lowerQuery)) {
            return pascalValues.includes(giftKeyword)
          }
          return false
        })
        if (!nameMatch && !keywordMatch) continue
      }
      ids.add(gift.id)
    }
    return ids
  }, [gifts, selectedKeywords, searchQuery, keywordToValue])

  const selectionLookup = useMemo(
    () => (enableEnhancementSelection ? buildSelectionLookup(selectedGiftIds) : null),
    [enableEnhancementSelection, selectedGiftIds]
  )

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
    <div className="bg-muted border border-border rounded-md p-6 h-[350px] overflow-y-auto scrollbar-hide">
      <ResponsiveCardGrid
        cardWidth={CARD_GRID.WIDTH.EGO_GIFT}
        cardHeight={96}
        mobileScale={0.8}
      >
        {gifts.slice(0, displayCount).map((gift) => {
          if (enableEnhancementSelection && onEnhancementSelect && selectionLookup) {
            const entry = selectionLookup.get(gift.id)
            const selected = entry !== undefined
            const enhancement = entry?.enhancement ?? 0
            return (
              <div key={gift.id} className={visibleIds.has(gift.id) ? '' : 'hidden'}>
                <EGOGiftSelectableCard
                  giftId={gift.id}
                  enhancement={enhancement}
                  maxEnhancement={gift.maxEnhancement}
                  isSelected={selected}
                  onEnhancementSelect={onEnhancementSelect}
                >
                  <EGOGiftCard
                    gift={gift}
                    enhancement={enhancement}
                    isSelected={selected}
                    enableHoverHighlight
                  />
                </EGOGiftSelectableCard>
              </div>
            )
          }

          if (!onGiftSelect) return null

          const isSelected = selectedGiftIds.has(gift.id)
          return (
            <div key={gift.id} className={visibleIds.has(gift.id) ? '' : 'hidden'}>
              <EGOGiftObservationCard
                giftId={gift.id}
                onSelect={onGiftSelect}
              >
                <EGOGiftCard
                  gift={gift}
                  isSelected={isSelected}
                  enableHoverHighlight
                />
              </EGOGiftObservationCard>
            </div>
          )
        })}
      </ResponsiveCardGrid>
    </div>
  )
}
