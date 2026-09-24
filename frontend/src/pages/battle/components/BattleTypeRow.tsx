import { useTranslation } from 'react-i18next'
import { useStore } from 'zustand'

import { BATTLE_FAMILIES } from '../lib/battleVocabulary'
import { battleTypeStore, selectBattleType, setBattleType } from '../stores/battleTypeStore'

export function BattleTypeRow() {
  const { t } = useTranslation('database')
  const entityType = useStore(battleTypeStore, selectBattleType)

  return (
    <div className="flex flex-wrap gap-2">
      {BATTLE_FAMILIES.map((family) => {
        const isSelected = entityType === family
        return (
          <button
            key={family}
            type="button"
            aria-pressed={isSelected}
            data-selected={isSelected}
            onClick={() => {
              setBattleType(family)
            }}
            className="selectable h-10 rounded-md border border-border px-4 text-sm"
          >
            {t(`battle.type.${family}`)}
          </button>
        )
      })}
    </div>
  )
}
