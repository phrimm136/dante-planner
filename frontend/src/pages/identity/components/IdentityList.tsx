import type { IdentityEntity } from '../types/IdentityTypes'
import { useSearchTermSources } from '@/shared/filter'
import { IDENTITY_LIST } from '../hooks/useIdentityListData'
import type { FilterStore } from '@/components/hooks/filterStore'
import { IDENTITY_GEOMETRY } from '@/shared/cardLayout'
import { FilteredEntityGrid, sortByReleaseDate } from '@/shared/filter'
import {
  buildIdentitySearchTerms,
  matchesIdentity,
  type IdentityFacetState,
} from '../lib/identityFilter'
import { IdentityCardLink } from './IdentityCardLink'

const EMPTY_NAMES: Record<string, string> = {}

interface IdentityListProps {
  identities: IdentityEntity[]
  store: FilterStore<IdentityFacetState>
}

export function IdentityList({ identities, store }: IdentityListProps) {
  const { names: identityNames, mappings } = useSearchTermSources(IDENTITY_LIST, EMPTY_NAMES)

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
