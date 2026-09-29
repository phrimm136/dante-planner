import { useEntityListI18n, type EntityListDataConfig } from '@/shared/entityCatalog'
import { battleDisplayName } from '../lib/battleListing'
import type { BattleCase } from '../lib/battleVocabulary'
import type { BattleNameList } from '../schemas/BattleSpecSchemas'

interface BattleEntityNameProps<TSpecList> {
  listConfig: EntityListDataConfig<TSpecList, BattleNameList>
  id: string
  mode: BattleCase
}

export function BattleEntityName<TSpecList>({
  listConfig,
  id,
  mode,
}: BattleEntityNameProps<TSpecList>) {
  const names = useEntityListI18n(listConfig)
  return battleDisplayName(names[id], mode, id)
}
