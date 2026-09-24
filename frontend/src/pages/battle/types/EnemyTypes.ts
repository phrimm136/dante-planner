import type { Entity } from '@/shared/filter'
import type { EnemyId } from '@/shared/gameData'
import type { EnemySpec } from '../schemas/EnemySchemas'

export type { EnemySpec, EnemySpecList, EnemyNameList } from '../schemas/EnemySchemas'

export type EnemyEntity = Entity<EnemyId, EnemySpec>
