import { Fragment, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { PASSIVE_INDICATOR_COLORS, SECTION_STYLES } from '@/lib/constants'
import { getDisplayFontForLanguage } from '@/lib/utils'
import { getEffectivePassives, getLockedPassives } from '../lib/identityPassiveSelection'

import type { PassiveId, Uptie } from '@/shared/gameData'

interface IdentityPassiveLists {
  battlePassiveList: PassiveId[][]
  supportPassiveList: PassiveId[][]
}

interface IdentityPassivesPaneProps {
  passives: IdentityPassiveLists
  uptieLevel: Uptie
  renderPassive: (passiveId: PassiveId, isLocked: boolean) => ReactNode
}

export function IdentityPassivesPane({
  passives,
  uptieLevel,
  renderPassive,
}: IdentityPassivesPaneProps) {
  const { t, i18n } = useTranslation(['database', 'common'])
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  const uptieIndex = uptieLevel - 1
  const effectiveBattlePassives = getEffectivePassives(passives.battlePassiveList, uptieIndex)
  const lockedBattlePassives = getLockedPassives(passives.battlePassiveList, uptieIndex)
  const effectiveSupportPassives = getEffectivePassives(passives.supportPassiveList, uptieIndex)
  const lockedSupportPassives = getLockedPassives(passives.supportPassiveList, uptieIndex)

  return (
    <div className="border rounded p-4 space-y-4">
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
          <Fragment key={passiveId}>{renderPassive(passiveId, false)}</Fragment>
        ))}
        {lockedBattlePassives.map((passiveId) => (
          <Fragment key={passiveId}>{renderPassive(passiveId, true)}</Fragment>
        ))}
        {effectiveBattlePassives.length === 0 && lockedBattlePassives.length === 0 && (
          <div className={SECTION_STYLES.TEXT.caption}>
            {t('identity.noBattlePassives', { ns: 'database' })}
          </div>
        )}
      </div>

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
          <Fragment key={passiveId}>{renderPassive(passiveId, false)}</Fragment>
        ))}
        {lockedSupportPassives.map((passiveId) => (
          <Fragment key={passiveId}>{renderPassive(passiveId, true)}</Fragment>
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
