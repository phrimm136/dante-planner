import type { AbEventSpecList } from '../schemas/AbEventSchemas'
import { CARD_GRID } from '@/lib/constants'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import { entriesSortedById, FilteredEntityGrid } from '@/shared/filter'
import { matchesAbEvent, type AbEventFacetState } from '../lib/abEventFilter'
import { AbEventCardLink } from './AbEventCardLink'

interface AbEventListProps {
  spec: AbEventSpecList
  store: FilterStore<AbEventFacetState>
}

/**
 * The abnormality event browser's card grid.
 *
 * Filter logic: AND between filter types, OR within each type.
 */
export function AbEventList({ spec, store }: AbEventListProps) {
  const sortedEvents = entriesSortedById(spec)

  return (
    <FilteredEntityGrid
      items={sortedEvents}
      getKey={([eventId]) => eventId}
      store={store}
      matches={([, entry], state) => matchesAbEvent(entry, state)}
      renderCard={([eventId, entry]) => (
        <AbEventCardLink eventId={eventId} hasImage={entry.hasImage} illustId={entry.illustId} />
      )}
      emptyStateKey="abEvent.emptyState"
      emptyStateFallback="No events found."
      cardWidth={CARD_GRID.WIDTH.AB_EVENT}
      cardHeight={CARD_GRID.HEIGHT.AB_EVENT}
      mobileScale={0.8}
    />
  )
}
