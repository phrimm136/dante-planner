import type { FilterStore } from '@/components/hooks/filterStore'
import { ASSISTANT_LIST } from '../hooks/useAssistantListData'
import { compareBattleIds } from '../lib/battleListing'
import { toAssistantEntity } from '../lib/assistant/assistantEntity'
import { matchesAssistant, type AssistantFacetState } from '../lib/assistant/assistantFilter'
import type { AssistantSpecList } from '../types/AssistantTypes'
import { BattleEntityList } from './BattleEntityList'

interface AssistantListProps {
  spec: AssistantSpecList
  store: FilterStore<AssistantFacetState>
}

export function AssistantList({ spec, store }: AssistantListProps) {
  const entities = Object.entries(spec)
    .map(([id, entry]) => toAssistantEntity(id, entry))
    .sort(compareBattleIds)

  return (
    <BattleEntityList
      family="assistant"
      items={entities}
      store={store}
      matches={matchesAssistant}
      listConfig={ASSISTANT_LIST}
    />
  )
}
