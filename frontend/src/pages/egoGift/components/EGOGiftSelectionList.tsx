import { useTranslation } from 'react-i18next'

import type { EGOGiftListItem } from '../types/EGOGiftTypes'
import type { EnhancementLevel } from '@/shared/gameData'
import { CARD_GRID, PROGRESSIVE_REVEAL, SECTION_STYLES } from '@/lib/constants'
import { applyFacets, useSearchMappingsDeferred } from '@/shared/filter'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { buildSelectionLookup } from '../lib/egoGiftEncoding'
import { EGO_GIFT_SELECTION_FACETS } from '../lib/egoGiftFilter'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { EGOGiftEnhancementCell, EGOGiftObservationCell } from './EGOGiftSelectionCell'

interface EGOGiftSelectionListProps {
  gifts: EGOGiftListItem[]
  selectedKeywords: Set<string>
  searchQuery: string
  selectedGiftIds: Set<string>
  onGiftSelect?: (giftId: string) => void
  enableEnhancementSelection?: boolean
  onEnhancementSelect?: (giftId: string, enhancement: EnhancementLevel) => void
}

/**
 * EGO gift selection list - CSS-based filtering with progressive rendering
 * Renders 10 cards initially, then 10 more per frame via requestAnimationFrame
 *
 * The map below is one compiler scope keyed on the selection and the reveal count,
 * so it re-runs on every toggle and every frame. Each cell carries its own memo
 * boundary, which is what keeps that from costing one render per gift.
 */
export function EGOGiftSelectionList({
  gifts,
  selectedKeywords,
  searchQuery,
  selectedGiftIds,
  onGiftSelect,
  enableEnhancementSelection = false,
  onEnhancementSelect,
}: EGOGiftSelectionListProps) {
  const { t } = useTranslation('database')
  const { keywordToValue } = useSearchMappingsDeferred()

  // Progressive rendering: start with one batch, add a batch per frame
  const displayCount = useProgressiveCount({
    total: gifts.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.CARD_BATCH,
  })

  const visibleIds = (() => {
    const ids = new Set<string>()
    const facetState = { selectedKeywords }

    for (const gift of gifts) {
      if (!applyFacets(gift, facetState, EGO_GIFT_SELECTION_FACETS)) continue

      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()
        const nameMatch = gift.name?.toLowerCase().includes(lowerQuery)
        const giftKeyword = gift.keyword ?? 'None'
        const keywordMatch = Array.from(keywordToValue.entries()).some(
          ([naturalLang, pascalValues]) => {
            if (naturalLang.includes(lowerQuery)) {
              return pascalValues.includes(giftKeyword)
            }
            return false
          },
        )
        if (!nameMatch && !keywordMatch) continue
      }
      ids.add(gift.id)
    }
    return ids
  })()

  const selectionLookup = enableEnhancementSelection ? buildSelectionLookup(selectedGiftIds) : null

  if (visibleIds.size === 0) {
    return (
      <div className={SECTION_STYLES.panel}>
        <div className="text-center text-muted-foreground py-8">{t('egoGift.emptyState')}</div>
      </div>
    )
  }

  return (
    <div className="bg-muted border border-border rounded-md p-6 max-h-[600px] overflow-y-auto">
      <ResponsiveCardGrid
        cardWidth={CARD_GRID.WIDTH.EGO_GIFT}
        cardHeight={CARD_GRID.HEIGHT.EGO_GIFT}
        mobileScale={CARD_GRID.MOBILE_SCALE.STANDARD}
        gap={8}
      >
        {gifts.slice(0, displayCount).map((gift) => {
          if (enableEnhancementSelection && onEnhancementSelect && selectionLookup) {
            const entry = selectionLookup.get(gift.id)
            return (
              <EGOGiftEnhancementCell
                key={gift.id}
                gift={gift}
                enhancement={entry?.enhancement ?? 0}
                isSelected={entry !== undefined}
                isVisible={visibleIds.has(gift.id)}
                onEnhancementSelect={onEnhancementSelect}
              />
            )
          }

          if (!onGiftSelect) return null

          return (
            <EGOGiftObservationCell
              key={gift.id}
              gift={gift}
              isSelected={selectedGiftIds.has(gift.id)}
              isVisible={visibleIds.has(gift.id)}
              onSelect={onGiftSelect}
            />
          )
        })}
      </ResponsiveCardGrid>
    </div>
  )
}
