import { memo } from 'react'
import { useTranslation } from 'react-i18next'

import type { IdentityListItem } from '../types/IdentityTypes'
import { useSearchMappingsDeferred, type SearchMappings } from '@/shared/filter'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { useIdentityListI18nDeferred } from '../hooks/useIdentityListData'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import { CARD_GRID, PROGRESSIVE_REVEAL, SECTION_STYLES } from '@/lib/constants'
import { sortByReleaseDate } from '@/shared/filter'
import {
  buildIdentitySearchTerms,
  matchesIdentity,
  type IdentityFacetState,
} from '../lib/identityFilter'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { FilteredCardSlot } from '@/shared/filter'
import { FilterEmptyState } from '@/shared/filter'
import { IdentityCardLink } from './IdentityCardLink'

interface IdentityListProps {
  identities: IdentityListItem[]
  store: FilterStore<IdentityFacetState>
}

/**
 * IdentityList - Renders every identity card once and lets each one subscribe to its
 * own visibility, so a filter toggle re-renders only the cards that changed.
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
  const { t } = useTranslation('database')
  // Non-suspending: returns empty mappings while loading, search won't match until loaded
  const mappings = useSearchMappingsDeferred()
  // Non-suspending: returns empty object while loading, name search won't match until loaded
  const identityNames = useIdentityListI18nDeferred()

  // Sort all identities once (stable order for CSS-based filtering)
  const sortedIdentities = sortByReleaseDate(identities)

  // Progressive rendering: start with one batch, add a batch per frame
  const displayCount = useProgressiveCount({
    total: sortedIdentities.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.CARD_BATCH,
  })

  const searchTerms = new Map(
    sortedIdentities.map((identity) => [
      identity.id,
      buildIdentitySearchTerms(identity, identityNames, mappings),
    ]),
  )

  return (
    <div className={SECTION_STYLES.panel}>
      <FilterEmptyState
        store={store}
        selectEmpty={(state) =>
          !sortedIdentities.some((identity) =>
            matchesIdentity(identity, state, searchTerms.get(identity.id) ?? []),
          )
        }
      >
        <div className="text-center text-muted-foreground py-8">{t('identity.emptyState')}</div>
      </FilterEmptyState>

      {/* Responsive grid layout with padding for sinner icons/bg */}
      <div className="pt-4">
        <ResponsiveCardGrid
          cardWidth={CARD_GRID.WIDTH.IDENTITY}
          cardHeight={CARD_GRID.HEIGHT.IDENTITY}
          mobileScale={0.8}
        >
          {sortedIdentities.slice(0, displayCount).map((identity) => (
            <IdentityCardCell
              key={identity.id}
              identity={identity}
              identityNames={identityNames}
              mappings={mappings}
              store={store}
            />
          ))}
        </ResponsiveCardGrid>
      </div>
    </div>
  )
}

interface IdentityCardCellProps {
  identity: IdentityListItem
  identityNames: Record<string, string>
  mappings: SearchMappings
  store: FilterStore<IdentityFacetState>
}

/**
 * One identity's slot, built inside a `map` and therefore outside the compiler's
 * reach: without `memo`, each progressive-reveal tick re-renders every card
 * already revealed.
 *
 * The cell derives its own search terms. Taking them as a prop would defeat the
 * comparison: the list's term map is rebuilt on every render, so each array would
 * arrive with a fresh identity.
 */
const IdentityCardCell = memo(function IdentityCardCell({
  identity,
  identityNames,
  mappings,
  store,
}: IdentityCardCellProps) {
  const terms = buildIdentitySearchTerms(identity, identityNames, mappings)

  return (
    <FilteredCardSlot
      store={store}
      selectVisible={(state) => matchesIdentity(identity, state, terms)}
      mobileScale={0.8}
      cardWidth={CARD_GRID.WIDTH.IDENTITY}
      cardHeight={CARD_GRID.HEIGHT.IDENTITY}
    >
      <IdentityCardLink identity={identity} />
    </FilteredCardSlot>
  )
})
