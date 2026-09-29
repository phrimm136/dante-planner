import { useTranslation } from 'react-i18next'
import { useStore } from 'zustand'

import { BATTLE_CASES } from '../lib/battleVocabulary'
import { battleModeStore, selectBattleMode, setBattleMode } from '../stores/battleModeStore'
import { SingleSelectRow } from './SingleSelectRow'

export function BattleModeRow() {
  const { t } = useTranslation('database')
  const mode = useStore(battleModeStore, selectBattleMode)

  return (
    <SingleSelectRow
      label={t('battle.facet.case')}
      options={BATTLE_CASES.map((battleCase) => ({
        value: battleCase,
        label: t(`battle.case.${battleCase}`),
      }))}
      selected={mode}
      onSelect={setBattleMode}
    />
  )
}
