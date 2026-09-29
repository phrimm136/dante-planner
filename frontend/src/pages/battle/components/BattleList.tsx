import type { FilterStore } from '@/components/hooks/filterStore'
import { FilteredEntityGrid, useSearchTermSources } from '@/shared/filter'
import { ASSISTANT_LIST } from '../hooks/useAssistantListData'
import { ENEMY_LIST } from '../hooks/useEnemyListData'
import { SINNER_EVENT_LIST } from '../hooks/useSinnerEventListData'
import { matchesBattle, type BattleFacetState } from '../lib/battleFilter'
import { buildBattleSearchTerms, type BattleCardItem } from '../lib/battleListing'
import type { BattleCase, BattleFamily } from '../lib/battleVocabulary'
import { BATTLE_GEOMETRY } from '../lib/cardLayout'
import type { BattleNameList } from '../schemas/BattleSpecSchemas'
import { BattleCard } from './BattleCard'

const EMPTY_NAMES: BattleNameList = {}

interface BattleListProps {
  items: readonly BattleCardItem[]
  mode: BattleCase
  store: FilterStore<BattleFacetState>
}

export function BattleList({ items, mode, store }: BattleListProps) {
  const names: Record<BattleFamily, BattleNameList> = {
    enemy: useSearchTermSources(ENEMY_LIST, EMPTY_NAMES, false).names,
    assistant: useSearchTermSources(ASSISTANT_LIST, EMPTY_NAMES, false).names,
    sinnerEvent: useSearchTermSources(SINNER_EVENT_LIST, EMPTY_NAMES, false).names,
  }

  return (
    <FilteredEntityGrid
      items={items}
      getKey={(item) => `${item.family}-${item.entity.id}`}
      store={store}
      matches={matchesBattle}
      buildTerms={(item) => buildBattleSearchTerms(item.entity.id, names[item.family], mode)}
      renderCard={(item) => (
        <BattleCard
          family={item.family}
          id={item.entity.id}
          spriteId={item.entity.spriteId}
          sprite={item.entity.sprite}
          mode={mode}
          enableHoverHighlight
          enableDetailLink
        />
      )}
      emptyStateKey="battle.emptyState"
      emptyStateFallback="No battle entities found."
      geometry={BATTLE_GEOMETRY}
    />
  )
}
