import type { EGOGiftListItem } from '../types/EGOGiftTypes'
import { CARD_GRID } from '@/lib/constants'
import { FilteredEntityGrid, useSearchMappingsDeferred } from '@/shared/filter'
import { useEGOGiftListI18nDeferred } from '../hooks/useEGOGiftListData'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import { sortEGOGifts } from '../lib/egoGiftSort'
import {
  buildEGOGiftSearchTerms,
  matchesEGOGift,
  type EGOGiftFacetState,
} from '../lib/egoGiftFilter'
import { EGOGiftCardLink } from './EGOGiftCardLink'

interface EGOGiftListProps {
  gifts: EGOGiftListItem[]
  store: FilterStore<EGOGiftFacetState>
}

/**
 * The EGO Gift browser's card grid.
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
  // Non-suspending: returns empty mappings while loading, search won't match until loaded
  const mappings = useSearchMappingsDeferred()
  // Non-suspending: returns empty object while loading, name search won't match until loaded
  const giftNames = useEGOGiftListI18nDeferred()

  // Sort all gifts once (stable order for CSS-based filtering)
  // Default sort: tier-first (higher tier first, then by keyword)
  const sortedGifts = sortEGOGifts(gifts, 'tier-first')

  return (
    <FilteredEntityGrid
      items={sortedGifts}
      getKey={(gift) => gift.id}
      store={store}
      matches={matchesEGOGift}
      buildTerms={(gift) => buildEGOGiftSearchTerms(gift, giftNames, mappings)}
      renderCard={(gift) => <EGOGiftCardLink gift={gift} />}
      emptyStateKey="egoGift.emptyState"
      cardWidth={CARD_GRID.WIDTH.EGO_GIFT}
      cardHeight={CARD_GRID.HEIGHT.EGO_GIFT}
      mobileScale={0.8}
    />
  )
}
