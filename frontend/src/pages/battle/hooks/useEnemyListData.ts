import type { z } from 'zod'
import {
  useEntityListI18n,
  useEntityListSpec,
  type EntityListDataConfig,
} from '@/shared/entityCatalog'
import { EnemyNameListSchema, EnemySpecListSchema } from '../schemas/EnemySchemas'

export const ENEMY_LIST: EntityListDataConfig<
  z.infer<typeof EnemySpecListSchema>,
  z.infer<typeof EnemyNameListSchema>
> = {
  kind: 'enemy',
  specImport: () => import('@static/data/enemySpecList.json'),
  specSchema: EnemySpecListSchema,
  i18nImport: (language) => import(`@static/i18n/${language}/enemyNameList.json`),
  i18nSchema: EnemyNameListSchema,
}

export function useEnemyListSpec() {
  return useEntityListSpec(ENEMY_LIST)
}

export function useEnemyListI18n() {
  return useEntityListI18n(ENEMY_LIST)
}
