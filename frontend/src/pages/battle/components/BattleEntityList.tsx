import type { FilterStore } from '@/components/hooks/filterStore'
import type { EntityListDataConfig } from '@/shared/entityCatalog'
import { FilteredEntityGrid, useSearchTermSources, type EntityMatcher } from '@/shared/filter'
import { buildBattleSearchTerms } from '../lib/battleListing'
import type { BattleFamily } from '../lib/battleVocabulary'
import { BATTLE_GEOMETRY } from '../lib/cardLayout'
import type { BattleNameList } from '../schemas/BattleSpecSchemas'
import { BattleCard } from './BattleCard'

const EMPTY_NAMES: BattleNameList = {}

interface BattleListItem {
  id: string
  spriteId: string
}

interface BattleEntityListProps<TItem extends BattleListItem, TState, TSpecList> {
  family: BattleFamily
  items: readonly TItem[]
  store: FilterStore<TState>
  matches: EntityMatcher<TItem, TState>
  listConfig: EntityListDataConfig<TSpecList, BattleNameList>
}

export function BattleEntityList<TItem extends BattleListItem, TState, TSpecList>({
  family,
  items,
  store,
  matches,
  listConfig,
}: BattleEntityListProps<TItem, TState, TSpecList>) {
  const { names } = useSearchTermSources(listConfig, EMPTY_NAMES, false)

  return (
    <FilteredEntityGrid
      items={items}
      getKey={(item) => item.id}
      store={store}
      matches={matches}
      buildTerms={(item) => buildBattleSearchTerms(item.id, names)}
      renderCard={(item) => (
        <BattleCard
          family={family}
          id={item.id}
          spriteId={item.spriteId}
          listConfig={listConfig}
          enableHoverHighlight
        />
      )}
      emptyStateKey="battle.emptyState"
      emptyStateFallback="No battle entities found."
      geometry={BATTLE_GEOMETRY}
    />
  )
}
