import { useTranslation } from 'react-i18next'

import { PASSIVE_INDICATOR_COLORS, SECTION_STYLES } from '@/lib/constants'
import { getDisplayFontForLanguage } from '@/lib/utils'
import { PassiveCardI18n } from './PassiveI18n'
import {
  getEffectivePassives,
  getLockedPassives,
  getPassiveCondition,
} from '../lib/identityPassiveSelection'

import type { IdentityData, Uptie } from '../types/IdentityTypes'

interface IdentityPassivesPaneProps {
  /** Identity ID for i18n lookup */
  id: string
  /** Passive lists and their conditions */
  passives: IdentityData['passives']
  /** Current uptie level (1-4) */
  uptieLevel: Uptie
}

/** Battle and support passives effective at the current uptie, plus locked previews. */
export function IdentityPassivesPane({ id, passives, uptieLevel }: IdentityPassivesPaneProps) {
  const { t, i18n } = useTranslation(['database', 'common'])
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  const uptieIndex = uptieLevel - 1
  const effectiveBattlePassives = getEffectivePassives(passives.battlePassiveList, uptieIndex)
  const lockedBattlePassives = getLockedPassives(passives.battlePassiveList, uptieIndex)
  const effectiveSupportPassives = getEffectivePassives(passives.supportPassiveList, uptieIndex)
  const lockedSupportPassives = getLockedPassives(passives.supportPassiveList, uptieIndex)

  return (
    <div className="border rounded p-4 space-y-4">
      {/* Battle Passive Section */}
      <div className="space-y-3">
        <div className="mb-4">
          <span
            className="font-bold px-8 py-1 text-md"
            style={{
              color: PASSIVE_INDICATOR_COLORS.TEXT,
              border: `2px solid ${PASSIVE_INDICATOR_COLORS.BORDER}`,
              ...displayStyle,
            }}
          >
            {t('passive.battle')}
          </span>
        </div>
        {effectiveBattlePassives.map((passiveId) => (
          <PassiveCardI18n
            key={passiveId}
            id={id}
            passiveId={passiveId}
            condition={getPassiveCondition(passives.conditions, passiveId)}
            isLocked={false}
          />
        ))}
        {lockedBattlePassives.map((passiveId) => (
          <PassiveCardI18n
            key={passiveId}
            id={id}
            passiveId={passiveId}
            condition={getPassiveCondition(passives.conditions, passiveId)}
            isLocked={true}
          />
        ))}
        {effectiveBattlePassives.length === 0 && lockedBattlePassives.length === 0 && (
          <div className={SECTION_STYLES.TEXT.caption}>
            {t('identity.noBattlePassives', { ns: 'database' })}
          </div>
        )}
      </div>

      {/* Support Passive Section */}
      <div className="space-y-3">
        <div className="mb-4 mt-8">
          <span
            className="font-bold px-8 py-1 text-md"
            style={{
              color: PASSIVE_INDICATOR_COLORS.TEXT,
              border: `2px solid ${PASSIVE_INDICATOR_COLORS.BORDER}`,
              ...displayStyle,
            }}
          >
            {t('passive.support')}
          </span>
        </div>
        {effectiveSupportPassives.map((passiveId) => (
          <PassiveCardI18n
            key={passiveId}
            id={id}
            passiveId={passiveId}
            condition={getPassiveCondition(passives.conditions, passiveId)}
            isLocked={false}
          />
        ))}
        {lockedSupportPassives.map((passiveId) => (
          <PassiveCardI18n
            key={passiveId}
            id={id}
            passiveId={passiveId}
            condition={getPassiveCondition(passives.conditions, passiveId)}
            isLocked={true}
          />
        ))}
        {effectiveSupportPassives.length === 0 && lockedSupportPassives.length === 0 && (
          <div className={SECTION_STYLES.TEXT.caption}>
            {t('identity.noSupportPassives', { ns: 'database' })}
          </div>
        )}
      </div>
    </div>
  )
}
