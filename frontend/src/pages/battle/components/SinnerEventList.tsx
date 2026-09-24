import type { FilterStore } from '@/components/hooks/filterStore'
import { SINNER_EVENT_LIST } from '../hooks/useSinnerEventListData'
import { compareBattleIds } from '../lib/battleListing'
import { toSinnerEventEntity } from '../lib/sinnerEvent/sinnerEventEntity'
import {
  matchesSinnerEvent,
  type SinnerEventFacetState,
} from '../lib/sinnerEvent/sinnerEventFilter'
import type { SinnerEventSpecList } from '../types/SinnerEventTypes'
import { BattleEntityList } from './BattleEntityList'

interface SinnerEventListProps {
  spec: SinnerEventSpecList
  store: FilterStore<SinnerEventFacetState>
}

export function SinnerEventList({ spec, store }: SinnerEventListProps) {
  const entities = Object.entries(spec)
    .map(([id, entry]) => toSinnerEventEntity(id, entry))
    .sort(compareBattleIds)

  return (
    <BattleEntityList
      family="sinnerEvent"
      items={entities}
      store={store}
      matches={matchesSinnerEvent}
      listConfig={SINNER_EVENT_LIST}
    />
  )
}
