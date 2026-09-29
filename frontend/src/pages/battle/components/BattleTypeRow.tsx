import { useTranslation } from 'react-i18next'
import { useStore } from 'zustand'

import { BATTLE_FAMILIES } from '../lib/battleVocabulary'
import { battleTypeStore, selectBattleType, setBattleType } from '../stores/battleTypeStore'
import { SingleSelectRow } from './SingleSelectRow'

export function BattleTypeRow() {
  const { t } = useTranslation('database')
  const entityType = useStore(battleTypeStore, selectBattleType)

  return (
    <SingleSelectRow
      options={BATTLE_FAMILIES.map((family) => ({
        value: family,
        label: t(`battle.type.${family}`),
      }))}
      selected={entityType}
      onSelect={setBattleType}
    />
  )
}
