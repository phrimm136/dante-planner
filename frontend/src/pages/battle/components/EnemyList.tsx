import type { FilterStore } from '@/components/hooks/filterStore'
import { ENEMY_LIST } from '../hooks/useEnemyListData'
import type { BattleCase } from '../lib/battleVocabulary'
import { matchesEnemy, type EnemyFacetState } from '../lib/enemy/enemyFilter'
import type { EnemyEntity } from '../types/EnemyTypes'
import { BattleEntityList } from './BattleEntityList'

interface EnemyListProps {
  items: readonly EnemyEntity[]
  mode: BattleCase
  store: FilterStore<EnemyFacetState>
}

export function EnemyList({ items, mode, store }: EnemyListProps) {
  return (
    <BattleEntityList
      family="enemy"
      items={items}
      mode={mode}
      store={store}
      matches={matchesEnemy}
      listConfig={ENEMY_LIST}
    />
  )
}
