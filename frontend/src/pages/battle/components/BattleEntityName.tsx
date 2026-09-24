import { useEntityListI18n, type EntityListDataConfig } from '@/shared/entityCatalog'
import type { BattleNameList } from '../schemas/BattleSpecSchemas'

interface BattleEntityNameProps<TSpecList> {
  listConfig: EntityListDataConfig<TSpecList, BattleNameList>
  id: string
}

export function BattleEntityName<TSpecList>({ listConfig, id }: BattleEntityNameProps<TSpecList>) {
  const names = useEntityListI18n(listConfig)
  return names[id] ?? id
}
