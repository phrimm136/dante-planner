import { memo } from 'react'
import { useTranslation } from 'react-i18next'

import type { AbEventSpecList } from '../schemas/AbEventSchemas'
import { CARD_GRID, PROGRESSIVE_REVEAL, SECTION_STYLES } from '@/lib/constants'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import { matchesAbEvent, type AbEventFacetState } from '../lib/abEventFilter'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { FilteredCardSlot } from '@/shared/filter'
import { FilterEmptyState } from '@/shared/filter'
import { AbEventCardLink } from './AbEventCardLink'

interface AbEventListProps {
  spec: AbEventSpecList
  store: FilterStore<AbEventFacetState>
}

/**
 * AbEventList - Renders every ab event card once and lets each one subscribe to its own
 * visibility, so a filter toggle re-renders only the cards that changed.
 *
 * Filter logic: AND between filter types, OR within each type.
 */
export function AbEventList({ spec, store }: AbEventListProps) {
  const { t } = useTranslation('database')

  const sortedEvents = Object.entries(spec).sort(([a], [b]) => a.localeCompare(b))

  // Progressive rendering
  const displayCount = useProgressiveCount({
    total: sortedEvents.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.CARD_BATCH,
  })

  return (
    <div className={SECTION_STYLES.panel}>
      <FilterEmptyState
        store={store}
        selectEmpty={(state) => !sortedEvents.some(([, entry]) => matchesAbEvent(entry, state))}
      >
        <div className="text-center text-muted-foreground py-8">
          {t('abEvent.emptyState', 'No events found.')}
        </div>
      </FilterEmptyState>

      <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.AB_EVENT} mobileScale={0.8}>
        {sortedEvents.slice(0, displayCount).map(([eventId, entry]) => (
          <AbEventCardCell key={eventId} eventId={eventId} entry={entry} store={store} />
        ))}
      </ResponsiveCardGrid>
    </div>
  )
}

interface AbEventCardCellProps {
  eventId: string
  entry: AbEventSpecList[string]
  store: FilterStore<AbEventFacetState>
}

/**
 * One event's slot, built inside a `map` and therefore outside the compiler's reach:
 * without `memo`, each progressive-reveal tick re-renders every card already revealed.
 */
const AbEventCardCell = memo(function AbEventCardCell({
  eventId,
  entry,
  store,
}: AbEventCardCellProps) {
  return (
    <FilteredCardSlot
      store={store}
      selectVisible={(state) => matchesAbEvent(entry, state)}
      mobileScale={0.8}
      cardWidth={CARD_GRID.WIDTH.AB_EVENT}
      cardHeight={CARD_GRID.HEIGHT.AB_EVENT}
    >
      <AbEventCardLink eventId={eventId} hasImage={entry.hasImage} illustId={entry.illustId} />
    </FilteredCardSlot>
  )
})
