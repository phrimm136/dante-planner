import { Fragment, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { SECTION_STYLES } from '@/lib/constants'
import { getEffectiveEgoPassives, getLockedEgoPassives } from '../lib/egoPassiveSelection'

import type { PassiveId, Threadspin } from '@/shared/gameData'

interface EGOPassiveLists {
  passiveList: PassiveId[][]
}

interface EGOPassivesPaneProps {
  passives: EGOPassiveLists
  threadspinLevel: Threadspin
  renderPassive: (passiveId: PassiveId, isLocked: boolean) => ReactNode
}

export function EGOPassivesPane({
  passives,
  threadspinLevel,
  renderPassive,
}: EGOPassivesPaneProps) {
  const { t } = useTranslation(['database', 'common'])

  const threadspinIndex = threadspinLevel - 1
  const effectivePassives = getEffectiveEgoPassives(passives.passiveList, threadspinIndex)
  const lockedPassives = getLockedEgoPassives(passives.passiveList, threadspinIndex)

  return (
    <div className="border rounded p-4 space-y-4">
      <div className="space-y-3">
        {effectivePassives.map((passiveId) => (
          <Fragment key={passiveId}>{renderPassive(passiveId, false)}</Fragment>
        ))}
        {lockedPassives.map((passiveId) => (
          <Fragment key={passiveId}>{renderPassive(passiveId, true)}</Fragment>
        ))}
        {effectivePassives.length === 0 && lockedPassives.length === 0 && (
          <div className={SECTION_STYLES.TEXT.caption}>{t('passive.none', 'No passives')}</div>
        )}
      </div>
    </div>
  )
}
