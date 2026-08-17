import type { IdentityListItem } from '../types/IdentityTypes'
import { useSearchTermSources } from '@/shared/filter'
import { IDENTITY_LIST } from '../hooks/useIdentityListData'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import { CARD_GRID } from '@/lib/constants'
import { FilteredEntityGrid, sortByReleaseDate, type CardGeometry } from '@/shared/filter'
import {
  buildIdentitySearchTerms,
  matchesIdentity,
  type IdentityFacetState,
} from '../lib/identityFilter'
import { IdentityCardLink } from './IdentityCardLink'

const EMPTY_NAMES: Record<string, string> = {}

const IDENTITY_GEOMETRY: CardGeometry = {
  cardWidth: CARD_GRID.WIDTH.IDENTITY,
  cardHeight: CARD_GRID.HEIGHT.IDENTITY,
  mobileScale: 0.8,
  fixedRowHeight: true,
}

interface IdentityListProps {
  identities: IdentityListItem[]
  store: FilterStore<IdentityFacetState>
}

/**
 * The identity browser's card grid.
 *
 * Filter Logic:
 * - All filter types use AND between each other
 * - Sinner: OR logic (any selected sinner)
 * - Keyword: AND logic (must have ALL selected keywords)
 * - Attribute: AND logic (must have ALL selected attributes)
 * - Attack Type: AND logic (must have ALL selected attack types)
 * - Rank: OR logic (any selected rank)
 * - Season: OR logic (any selected season)
 * - Association: OR logic (any selected association)
 * - Search: OR logic (name OR keyword OR trait)
 */
export function IdentityList({ identities, store }: IdentityListProps) {
  const { names: identityNames, mappings } = useSearchTermSources(IDENTITY_LIST, EMPTY_NAMES)

  // Sort all identities once (stable order for CSS-based filtering)
  const sortedIdentities = sortByReleaseDate(identities)

  return (
    <FilteredEntityGrid
      items={sortedIdentities}
      getKey={(identity) => identity.id}
      store={store}
      matches={matchesIdentity}
      buildTerms={(identity) => buildIdentitySearchTerms(identity, identityNames, mappings)}
      renderCard={(identity) => <IdentityCardLink identity={identity} />}
      emptyStateKey="identity.emptyState"
      geometry={IDENTITY_GEOMETRY}
      gridWrapperClassName="pt-4"
    />
  )
}
