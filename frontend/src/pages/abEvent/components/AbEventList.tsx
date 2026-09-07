import type { AbEventNameList, AbEventSpecList } from '../schemas/AbEventSchemas'
import { CARD_GRID } from '@/lib/constants'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import {
  entriesSortedById,
  FilteredEntityGrid,
  useSearchTermSources,
  type CardGeometry,
} from '@/shared/filter'
import { AB_EVENT_LIST } from '../hooks/useAbEventListData'
import {
  buildAbEventSearchTerms,
  matchesAbEvent,
  type AbEventFacetState,
} from '../lib/abEventFilter'
import { AbEventCardLink } from './AbEventCardLink'

const EMPTY_DESCS: AbEventNameList = {}

const AB_EVENT_GEOMETRY: CardGeometry = {
  cardWidth: CARD_GRID.WIDTH.AB_EVENT,
  cardHeight: CARD_GRID.HEIGHT.AB_EVENT,
  mobileScale: 0.8,
}

interface AbEventListProps {
  spec: AbEventSpecList
  store: FilterStore<AbEventFacetState>
}

/**
 * The abnormality event browser's card grid.
 *
 * Filter logic: AND between filter types, OR within each type; search matches the
 * event description.
 */
export function AbEventList({ spec, store }: AbEventListProps) {
  const { names: descs } = useSearchTermSources(AB_EVENT_LIST, EMPTY_DESCS, false)
  const sortedEvents = entriesSortedById(spec)

  return (
    <FilteredEntityGrid
      items={sortedEvents}
      getKey={([eventId]) => eventId}
      store={store}
      matches={matchesAbEvent}
      buildTerms={([eventId]) => buildAbEventSearchTerms(eventId, descs)}
      renderCard={([eventId, entry]) => (
        <AbEventCardLink eventId={eventId} hasImage={entry.hasImage} illustId={entry.illustId} />
      )}
      emptyStateKey="abEvent.emptyState"
      emptyStateFallback="No events found."
      geometry={AB_EVENT_GEOMETRY}
    />
  )
}
