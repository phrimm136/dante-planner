import { useTranslation } from 'react-i18next'
import { useEntityListI18n } from '@/shared/entityCatalog'
import { battleCardName, battleEventTitle } from '../lib/battleListing'
import type { BattleFamily } from '../lib/battleVocabulary'
import { BATTLE_NAME_LISTS } from './battleNameLists'
import { useBattleFacetNames } from './useBattleFacetNames'
import { useSinnerEventListSpec } from './useSinnerEventListData'

export function useBattleEventTitle(): (family: BattleFamily, id: string) => string | undefined {
  const { t } = useTranslation('database')
  const chapters = new Map(
    Object.entries(useSinnerEventListSpec()).map(([id, spec]) => [id, spec.chapters]),
  )
  const facetNames = useBattleFacetNames()

  return (family, id) =>
    battleEventTitle(family, chapters.get(id) ?? [], facetNames.chapters, (key, options) =>
      t(key, options),
    )
}

export function useBattleCardNames(family: BattleFamily): Record<string, string> {
  const names = useEntityListI18n(BATTLE_NAME_LISTS[family])
  const eventTitle = useBattleEventTitle()

  return Object.fromEntries(
    Object.entries(names).map(([id, entry]) => [
      id,
      battleCardName(entry.name, eventTitle(family, id)),
    ]),
  )
}
