import type { FilterStore } from '@/components/hooks/filterStore'
import { ASSISTANT_LIST } from '../hooks/useAssistantListData'
import { matchesAssistant, type AssistantFacetState } from '../lib/assistant/assistantFilter'
import type { BattleCase } from '../lib/battleVocabulary'
import type { AssistantEntity } from '../types/AssistantTypes'
import { BattleEntityList } from './BattleEntityList'

interface AssistantListProps {
  items: readonly AssistantEntity[]
  mode: BattleCase
  store: FilterStore<AssistantFacetState>
}

export function AssistantList({ items, mode, store }: AssistantListProps) {
  return (
    <BattleEntityList
      family="assistant"
      items={items}
      mode={mode}
      store={store}
      matches={matchesAssistant}
      listConfig={ASSISTANT_LIST}
    />
  )
}
