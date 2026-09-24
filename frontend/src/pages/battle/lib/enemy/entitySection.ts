import type { EntitySection } from '@/shared/filter'
import { EnemyIdSchema } from '@/shared/gameData'

import type { EnemyId } from '@/shared/gameData'
import { EnemySpecSchema, EnemySpecListSchema } from '../../schemas/EnemySchemas'
import type { EnemySpec } from '../../types/EnemyTypes'
import { ENEMY_FACETS } from './enemyFilter'
import type { EnemyFacetState } from './enemyFilter'
import { toEnemyEntity } from './enemyEntity'

export const section: EntitySection<EnemyId, EnemySpec, EnemyFacetState> = {
  name: 'battle/enemy',
  specFile: 'enemySpecList.json',
  specListSchema: EnemySpecListSchema,
  specEntrySchema: EnemySpecSchema,
  idSchema: EnemyIdSchema,
  toEntity: toEnemyEntity,
  facets: ENEMY_FACETS,
}
