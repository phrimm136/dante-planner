import type { EGOGiftEntity } from '../types/EGOGiftTypes'
import { EGO_GIFT_GEOMETRY } from '@/shared/cardLayout'
import { FilteredEntityGrid, useSearchTermSources } from '@/shared/filter'
import { EGO_GIFT_LIST } from '../hooks/useEGOGiftListData'
import type { FilterStore } from '@/components/hooks/filterStore'
import { sortEGOGifts } from '../lib/egoGiftSort'
import {
  buildEGOGiftSearchTerms,
  matchesEGOGift,
  type EGOGiftFacetState,
} from '../lib/egoGiftFilter'
import { EGOGiftCardLink } from './EGOGiftCardLink'

const EMPTY_NAMES: Record<string, string> = {}

interface EGOGiftListProps {
  gifts: EGOGiftEntity[]
  store: FilterStore<EGOGiftFacetState>
}

export function EGOGiftList({ gifts, store }: EGOGiftListProps) {
  const { names: giftNames, mappings } = useSearchTermSources(EGO_GIFT_LIST, EMPTY_NAMES)

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
      geometry={EGO_GIFT_GEOMETRY}
    />
  )
}
