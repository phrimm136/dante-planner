import type { FilterStore } from '@/components/hooks/filterStore'
import { SINNER_EVENT_LIST } from '../hooks/useSinnerEventListData'
import type { BattleCase } from '../lib/battleVocabulary'
import {
  matchesSinnerEvent,
  type SinnerEventFacetState,
} from '../lib/sinnerEvent/sinnerEventFilter'
import type { SinnerEventEntity } from '../types/SinnerEventTypes'
import { BattleEntityList } from './BattleEntityList'

interface SinnerEventListProps {
  items: readonly SinnerEventEntity[]
  mode: BattleCase
  store: FilterStore<SinnerEventFacetState>
}

export function SinnerEventList({ items, mode, store }: SinnerEventListProps) {
  return (
    <BattleEntityList
      family="sinnerEvent"
      items={items}
      mode={mode}
      store={store}
      matches={matchesSinnerEvent}
      listConfig={SINNER_EVENT_LIST}
    />
  )
}
