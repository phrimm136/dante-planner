import { useEntityListI18n } from '@/shared/entityCatalog'
import { BATTLE_NAME_LISTS } from '../hooks/battleNameLists'
import { battleListName } from '../lib/battleListing'
import type { BattleCase, BattleFamily } from '../lib/battleVocabulary'

interface BattleEntityNameProps {
  family: BattleFamily
  id: string
  mode: BattleCase
  identityTitle: string | undefined
}

export function BattleEntityName({ family, id, mode, identityTitle }: BattleEntityNameProps) {
  const names = useEntityListI18n(BATTLE_NAME_LISTS[family])
  return battleListName(names[id], mode, id, identityTitle)
}
