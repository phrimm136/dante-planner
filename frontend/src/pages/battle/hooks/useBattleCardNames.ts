import { useTranslation } from 'react-i18next'
import { useIdentityListI18n } from '@/pages/identity'
import { useEntityListI18n } from '@/shared/entityCatalog'
import {
  battleCardName,
  battleEventTitle,
  envyIdentityTitle,
  withIdentityTitle,
} from '../lib/battleListing'
import type { BattleFamily } from '../lib/battleVocabulary'
import { BATTLE_NAME_LISTS } from './battleNameLists'
import { useBattleFacetNames } from './useBattleFacetNames'
import { useEnemyListSpec } from './useEnemyListData'
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
  const personalityIds = new Map(
    Object.entries(useEnemyListSpec()).map(([id, spec]) => [id, spec.personalityId]),
  )
  const identityTitles = useIdentityListI18n()
  const identityTitle = (id: string) =>
    envyIdentityTitle(family === 'enemy' ? personalityIds.get(id) : undefined, identityTitles)

  return Object.fromEntries(
    Object.entries(names).map(([id, entry]) => [
      id,
      battleCardName(withIdentityTitle(entry.name, identityTitle(id)), eventTitle(family, id)),
    ]),
  )
}
