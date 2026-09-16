import type { AbEventNameList, AbEventSpecList } from '../schemas/AbEventSchemas'
import { AB_EVENT_GEOMETRY } from '../lib/cardLayout'
import type { FilterStore } from '@/components/hooks/filterStore'
import { entriesSortedById, FilteredEntityGrid, useSearchTermSources } from '@/shared/filter'
import { AB_EVENT_LIST } from '../hooks/useAbEventListData'
import {
  buildAbEventSearchTerms,
  matchesAbEvent,
  type AbEventFacetState,
} from '../lib/abEventFilter'
import { AbEventCardLink } from './AbEventCardLink'

const EMPTY_DESCS: AbEventNameList = {}

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
