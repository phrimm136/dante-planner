import type { AbEventNameList, AbEventSpecList } from '../schemas/AbEventSchemas'
import { AB_EVENT_GEOMETRY } from '../lib/cardLayout'
import type { FilterStore } from '@/components/hooks/filterStore'
import { FilteredEntityGrid, useSearchTermSources } from '@/shared/filter'
import { AB_EVENT_LIST } from '../hooks/useAbEventListData'
import {
  buildAbEventSearchTerms,
  matchesAbEvent,
  type AbEventFacetState,
} from '../lib/abEventFilter'
import { toAbEventEntity } from '../lib/abEventEntity'
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
  const sortedEvents = Object.entries(spec)
    .map(([id, entry]) => toAbEventEntity(id, entry))
    .sort((a, b) => a.id.localeCompare(b.id))

  return (
    <FilteredEntityGrid
      items={sortedEvents}
      getKey={(item) => item.id}
      store={store}
      matches={matchesAbEvent}
      buildTerms={(item) => buildAbEventSearchTerms(item.id, descs)}
      renderCard={(item) => (
        <AbEventCardLink eventId={item.id} hasImage={item.hasImage} illustId={item.illustId} />
      )}
      emptyStateKey="abEvent.emptyState"
      emptyStateFallback="No events found."
      geometry={AB_EVENT_GEOMETRY}
    />
  )
}
