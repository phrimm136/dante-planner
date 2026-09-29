import { useEntityListI18n } from '@/shared/entityCatalog'
import { BATTLE_NAME_LISTS } from '../hooks/battleNameLists'
import { battleDisplayName } from '../lib/battleListing'
import type { BattleCase, BattleFamily } from '../lib/battleVocabulary'

interface BattleEntityNameProps {
  family: BattleFamily
  id: string
  mode: BattleCase
}

export function BattleEntityName({ family, id, mode }: BattleEntityNameProps) {
  const names = useEntityListI18n(BATTLE_NAME_LISTS[family])
  return battleDisplayName(names[id], mode, id)
}
