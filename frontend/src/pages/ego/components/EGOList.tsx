import { memo } from 'react'
import { useTranslation } from 'react-i18next'

import type { EGOListItem } from '../types/EGOTypes'
import { useSearchMappingsDeferred, type SearchMappings } from '@/shared/filter'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { useEGOListI18nDeferred } from '../hooks/useEGOListData'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import { CARD_GRID, PROGRESSIVE_REVEAL, SECTION_STYLES } from '@/lib/constants'
import { sortEGOByDate } from '@/shared/filter'
import { buildEGOSearchTerms, matchesEGO, type EGOFacetState } from '../lib/egoFilter'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { FilteredCardSlot } from '@/shared/filter'
import { FilterEmptyState } from '@/shared/filter'
import { EGOCardLink } from './EGOCardLink'

interface EGOListProps {
  egos: EGOListItem[]
  store: FilterStore<EGOFacetState>
}

/**
 * EGOList - Renders every EGO card once and lets each one subscribe to its own
 * visibility, so a filter toggle re-renders only the cards that changed.
 */
export function EGOList({ egos, store }: EGOListProps) {
  const { t } = useTranslation('database')
  // Non-suspending: returns empty mappings while loading, search won't match until loaded
  const mappings = useSearchMappingsDeferred()
  // Non-suspending: returns empty object while loading, name search won't match until loaded
  const egoNames = useEGOListI18nDeferred()

  // Sort all EGOs once (stable order for CSS-based filtering)
  const sortedEGOs = sortEGOByDate(egos)

  // Progressive rendering: start with one batch, add a batch per frame
  const displayCount = useProgressiveCount({
    total: sortedEGOs.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.CARD_BATCH,
  })

  const searchTerms = new Map(
    sortedEGOs.map((ego) => [ego.id, buildEGOSearchTerms(ego, egoNames, mappings)]),
  )

  return (
    <div className={SECTION_STYLES.panel}>
      <FilterEmptyState
        store={store}
        selectEmpty={(state) =>
          !sortedEGOs.some((ego) => matchesEGO(ego, state, searchTerms.get(ego.id) ?? []))
        }
      >
        <div className="text-center text-muted-foreground py-8">{t('ego.emptyState')}</div>
      </FilterEmptyState>

      {/* Responsive grid layout */}
      <div className="pt-4">
        <ResponsiveCardGrid
          cardWidth={CARD_GRID.WIDTH.EGO}
          cardHeight={CARD_GRID.HEIGHT.EGO}
          mobileScale={0.8}
        >
          {sortedEGOs.slice(0, displayCount).map((ego) => (
            <EGOCardCell
              key={ego.id}
              ego={ego}
              egoNames={egoNames}
              mappings={mappings}
              store={store}
            />
          ))}
        </ResponsiveCardGrid>
      </div>
    </div>
  )
}

interface EGOCardCellProps {
  ego: EGOListItem
  egoNames: Record<string, string>
  mappings: SearchMappings
  store: FilterStore<EGOFacetState>
}

/**
 * One EGO's slot, built inside a `map` and therefore outside the compiler's reach:
 * without `memo`, each progressive-reveal tick re-renders every card already revealed.
 *
 * The cell derives its own search terms. Taking them as a prop would defeat the
 * comparison: the list's term map is rebuilt on every render, so each array would
 * arrive with a fresh identity.
 */
const EGOCardCell = memo(function EGOCardCell({
  ego,
  egoNames,
  mappings,
  store,
}: EGOCardCellProps) {
  const terms = buildEGOSearchTerms(ego, egoNames, mappings)

  return (
    <FilteredCardSlot
      store={store}
      selectVisible={(state) => matchesEGO(ego, state, terms)}
      mobileScale={0.8}
      cardWidth={CARD_GRID.WIDTH.EGO}
      cardHeight={CARD_GRID.HEIGHT.EGO}
    >
      <EGOCardLink ego={ego} />
    </FilteredCardSlot>
  )
})
