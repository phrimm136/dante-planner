import type { CSSProperties, ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { PASSIVE_INDICATOR_COLORS, PASSIVE_TAB_UNITS, UNIT_PANEL_COLORS } from '@/lib/constants'
import { clientLength, getDisplayFontForLanguage } from '@/lib/utils'
import { getEffectivePassives, getLockedPassives } from '../lib/identityPassiveSelection'
import { PassiveRun, PassiveTab } from './EGOPassivesPane'
import { PassivePlaceholder } from './PassivePlaceholder'

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

const HEADER_STYLE: CSSProperties = {
  color: UNIT_PANEL_COLORS.PASSIVE_HEADER,
  border: `2px solid ${PASSIVE_INDICATOR_COLORS.BORDER}`,
  marginLeft: clientLength(PASSIVE_TAB_UNITS.HEADER.LEFT),
  width: clientLength(PASSIVE_TAB_UNITS.HEADER.WIDTH),
  height: clientLength(PASSIVE_TAB_UNITS.HEADER.HEIGHT),
  fontSize: clientLength(PASSIVE_TAB_UNITS.HEADER.FONT),
  marginBottom: clientLength(PASSIVE_TAB_UNITS.HEADER.AFTER),
}

const SUPPORT_HEADER_STYLE: CSSProperties = {
  ...HEADER_STYLE,
  marginTop: clientLength(PASSIVE_TAB_UNITS.HEADER.SUPPORT_BEFORE),
}

export function IdentityPassivesPane({
  passives,
  uptieLevel,
  renderPassive,
}: IdentityPassivesPaneProps) {
  const { t, i18n } = useTranslation(['database', 'common'])
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  const uptieIndex = uptieLevel - 1
  const battlePassives = [
    ...getEffectivePassives(passives.battlePassiveList, uptieIndex).map((id) => ({
      id,
      isLocked: false,
    })),
    ...getLockedPassives(passives.battlePassiveList, uptieIndex).map((id) => ({
      id,
      isLocked: true,
    })),
  ]
  const supportPassives = [
    ...getEffectivePassives(passives.supportPassiveList, uptieIndex).map((id) => ({
      id,
      isLocked: false,
    })),
    ...getLockedPassives(passives.supportPassiveList, uptieIndex).map((id) => ({
      id,
      isLocked: true,
    })),
  ]

  return (
    <PassiveTab>
      <div
        data-slot="passive-header"
        className="font-bold flex items-center justify-center"
        style={{ ...HEADER_STYLE, ...displayStyle }}
      >
        {t('passive.battle')}
      </div>
      <PassiveRun passives={battlePassives} renderPassive={renderPassive} />
      {battlePassives.length === 0 && (
        <PassivePlaceholder>
          {t('identity.noBattlePassives', { ns: 'database' })}
        </PassivePlaceholder>
      )}
      <div
        data-slot="passive-header"
        className="font-bold flex items-center justify-center"
        style={{ ...SUPPORT_HEADER_STYLE, ...displayStyle }}
      >
        {t('passive.support')}
      </div>
      <PassiveRun passives={supportPassives} renderPassive={renderPassive} />
      {supportPassives.length === 0 && (
        <PassivePlaceholder>
          {t('identity.noSupportPassives', { ns: 'database' })}
        </PassivePlaceholder>
      )}
    </PassiveTab>
  )
}
