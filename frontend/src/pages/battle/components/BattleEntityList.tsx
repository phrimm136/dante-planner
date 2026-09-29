import type { FilterStore } from '@/components/hooks/filterStore'
import type { EntityListDataConfig } from '@/shared/entityCatalog'
import { FilteredEntityGrid, useSearchTermSources, type EntityMatcher } from '@/shared/filter'
import { buildBattleSearchTerms, type BattleListItem } from '../lib/battleListing'
import type { BattleCase, BattleFamily } from '../lib/battleVocabulary'
import { BATTLE_GEOMETRY } from '../lib/cardLayout'
import type { BattleNameList } from '../schemas/BattleSpecSchemas'
import { BattleCard } from './BattleCard'

const EMPTY_NAMES: BattleNameList = {}

interface BattleEntityListProps<TItem extends BattleListItem, TState, TSpecList> {
  family: BattleFamily
  items: readonly TItem[]
  mode: BattleCase
  store: FilterStore<TState>
  matches: EntityMatcher<TItem, TState>
  listConfig: EntityListDataConfig<TSpecList, BattleNameList>
}

export function BattleEntityList<TItem extends BattleListItem, TState, TSpecList>({
  family,
  items,
  mode,
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
      buildTerms={(item) => buildBattleSearchTerms(item.id, names, mode)}
      renderCard={(item) => (
        <BattleCard
          family={family}
          id={item.id}
          spriteId={item.spriteId}
          sprite={item.sprite}
          mode={mode}
          listConfig={listConfig}
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
