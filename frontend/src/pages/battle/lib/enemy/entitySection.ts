import type { EntitySection } from '@/shared/filter'
import { EnemyIdSchema } from '@/shared/gameData'

import type { EnemyId } from '@/shared/gameData'
import { EnemySpecSchema, EnemySpecListSchema } from '../../schemas/EnemySchemas'
import type { EnemySpec } from '../../types/EnemyTypes'
import { DRILL_DOWN_FACETS, type BattleDrillDownState } from '../battleFilter'
import { toEnemyEntity } from './enemyEntity'

export const section: EntitySection<EnemyId, EnemySpec, BattleDrillDownState> = {
  name: 'battle/enemy',
  specFile: 'enemySpecList.json',
  specListSchema: EnemySpecListSchema,
  specEntrySchema: EnemySpecSchema,
  idSchema: EnemyIdSchema,
  toEntity: toEnemyEntity,
  facets: DRILL_DOWN_FACETS,
}
