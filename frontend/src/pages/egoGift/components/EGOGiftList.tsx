import { memo } from 'react'
import { useTranslation } from 'react-i18next'

import type { EGOGiftListItem } from '../types/EGOGiftTypes'
import { CARD_GRID, PROGRESSIVE_REVEAL, SECTION_STYLES } from '@/lib/constants'
import { useSearchMappingsDeferred, type SearchMappings } from '@/shared/filter'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { useEGOGiftListI18nDeferred } from '../hooks/useEGOGiftListData'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import { sortEGOGifts } from '../lib/egoGiftSort'
import {
  buildEGOGiftSearchTerms,
  matchesEGOGift,
  type EGOGiftFacetState,
} from '../lib/egoGiftFilter'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { FilteredCardSlot } from '@/shared/filter'
import { FilterEmptyState } from '@/shared/filter'
import { EGOGiftCardLink } from './EGOGiftCardLink'

interface EGOGiftListProps {
  gifts: EGOGiftListItem[]
  store: FilterStore<EGOGiftFacetState>
}

/**
 * EGOGiftList - Renders every EGO Gift card once and lets each one subscribe to its own
 * visibility, so a filter toggle re-renders only the cards that changed.
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
export function EGOGiftList({ gifts, store }: EGOGiftListProps) {
  const { t } = useTranslation('database')
  // Non-suspending: returns empty mappings while loading, search won't match until loaded
  const mappings = useSearchMappingsDeferred()
  // Non-suspending: returns empty object while loading, name search won't match until loaded
  const giftNames = useEGOGiftListI18nDeferred()

  // Sort all gifts once (stable order for CSS-based filtering)
  // Default sort: tier-first (higher tier first, then by keyword)
  const sortedGifts = sortEGOGifts(gifts, 'tier-first')

  // Progressive rendering: start with one batch, add a batch per frame
  const displayCount = useProgressiveCount({
    total: sortedGifts.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.CARD_BATCH,
  })

  const searchTerms = new Map(
    sortedGifts.map((gift) => [gift.id, buildEGOGiftSearchTerms(gift, giftNames, mappings)]),
  )

  return (
    <div className={SECTION_STYLES.panel}>
      <FilterEmptyState
        store={store}
        selectEmpty={(state) =>
          !sortedGifts.some((gift) => matchesEGOGift(gift, state, searchTerms.get(gift.id) ?? []))
        }
      >
        <div className="text-center text-muted-foreground py-8">{t('egoGift.emptyState')}</div>
      </FilterEmptyState>

      <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.EGO_GIFT} mobileScale={0.8}>
        {sortedGifts.slice(0, displayCount).map((gift) => (
          <EGOGiftCardCell
            key={gift.id}
            gift={gift}
            giftNames={giftNames}
            mappings={mappings}
            store={store}
          />
        ))}
      </ResponsiveCardGrid>
    </div>
  )
}

interface EGOGiftCardCellProps {
  gift: EGOGiftListItem
  giftNames: Record<string, string>
  mappings: SearchMappings
  store: FilterStore<EGOGiftFacetState>
}

/**
 * One gift's slot, built inside a `map` and therefore outside the compiler's reach:
 * without `memo`, each progressive-reveal tick re-renders every card already revealed.
 *
 * The cell derives its own search terms. Taking them as a prop would defeat the
 * comparison: the list's term map is rebuilt on every render, so each array would
 * arrive with a fresh identity.
 */
const EGOGiftCardCell = memo(function EGOGiftCardCell({
  gift,
  giftNames,
  mappings,
  store,
}: EGOGiftCardCellProps) {
  const terms = buildEGOGiftSearchTerms(gift, giftNames, mappings)

  return (
    <FilteredCardSlot
      store={store}
      selectVisible={(state) => matchesEGOGift(gift, state, terms)}
      mobileScale={0.8}
      cardWidth={CARD_GRID.WIDTH.EGO_GIFT}
      cardHeight={CARD_GRID.HEIGHT.EGO_GIFT}
    >
      <EGOGiftCardLink gift={gift} />
    </FilteredCardSlot>
  )
})
