import type { EGOListItem } from '../types/EGOTypes'
import { useSearchMappingsDeferred } from '@/shared/filter'
import { useEGOListI18nDeferred } from '../hooks/useEGOListData'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import { CARD_GRID } from '@/lib/constants'
import { FilteredEntityGrid, sortEGOByDate } from '@/shared/filter'
import { buildEGOSearchTerms, matchesEGO, type EGOFacetState } from '../lib/egoFilter'
import { EGOCardLink } from './EGOCardLink'

interface EGOListProps {
  egos: EGOListItem[]
  store: FilterStore<EGOFacetState>
}

/** The EGO browser's card grid. */
export function EGOList({ egos, store }: EGOListProps) {
  // Non-suspending: returns empty mappings while loading, search won't match until loaded
  const mappings = useSearchMappingsDeferred()
  // Non-suspending: returns empty object while loading, name search won't match until loaded
  const egoNames = useEGOListI18nDeferred()

  // Sort all EGOs once (stable order for CSS-based filtering)
  const sortedEGOs = sortEGOByDate(egos)

  return (
    <FilteredEntityGrid
      items={sortedEGOs}
      getKey={(ego) => ego.id}
      store={store}
      matches={matchesEGO}
      buildTerms={(ego) => buildEGOSearchTerms(ego, egoNames, mappings)}
      renderCard={(ego) => <EGOCardLink ego={ego} />}
      emptyStateKey="ego.emptyState"
      cardWidth={CARD_GRID.WIDTH.EGO}
      cardHeight={CARD_GRID.HEIGHT.EGO}
      mobileScale={0.8}
      fixedRowHeight
      gridWrapperClassName="pt-4"
    />
  )
}
