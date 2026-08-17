import type { EGOListItem } from '../types/EGOTypes'
import { useSearchTermSources } from '@/shared/filter'
import { EGO_LIST } from '../hooks/useEGOListData'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import { CARD_GRID } from '@/lib/constants'
import { FilteredEntityGrid, sortEGOByDate, type CardGeometry } from '@/shared/filter'
import { buildEGOSearchTerms, matchesEGO, type EGOFacetState } from '../lib/egoFilter'
import { EGOCardLink } from './EGOCardLink'

const EMPTY_NAMES: Record<string, string> = {}

const EGO_GEOMETRY: CardGeometry = {
  cardWidth: CARD_GRID.WIDTH.EGO,
  cardHeight: CARD_GRID.HEIGHT.EGO,
  mobileScale: 0.8,
  fixedRowHeight: true,
}

interface EGOListProps {
  egos: EGOListItem[]
  store: FilterStore<EGOFacetState>
}

/** The EGO browser's card grid. */
export function EGOList({ egos, store }: EGOListProps) {
  const { names: egoNames, mappings } = useSearchTermSources(EGO_LIST, EMPTY_NAMES)

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
      geometry={EGO_GEOMETRY}
      gridWrapperClassName="pt-4"
    />
  )
}
