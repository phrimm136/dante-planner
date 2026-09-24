import type { FilterStore } from '@/components/hooks/filterStore'
import { ENEMY_LIST } from '../hooks/useEnemyListData'
import { compareBattleIds } from '../lib/battleListing'
import { toEnemyEntity } from '../lib/enemy/enemyEntity'
import { matchesEnemy, type EnemyFacetState } from '../lib/enemy/enemyFilter'
import type { EnemySpecList } from '../types/EnemyTypes'
import { BattleEntityList } from './BattleEntityList'

interface EnemyListProps {
  spec: EnemySpecList
  store: FilterStore<EnemyFacetState>
}

export function EnemyList({ spec, store }: EnemyListProps) {
  const entities = Object.entries(spec)
    .map(([id, entry]) => toEnemyEntity(id, entry))
    .sort(compareBattleIds)

  return (
    <BattleEntityList
      family="enemy"
      items={entities}
      store={store}
      matches={matchesEnemy}
      listConfig={ENEMY_LIST}
    />
  )
}
