import { createEntityBuilder } from '@/shared/filter'
import { EnemyIdSchema } from '@/shared/gameData'

import type { EnemyId } from '@/shared/gameData'
import type { EnemySpec } from '../../types/EnemyTypes'

export const toEnemyEntity = createEntityBuilder<EnemyId, EnemySpec>(EnemyIdSchema)
