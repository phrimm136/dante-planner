import { useTranslation } from 'react-i18next'

import { SECTION_STYLES } from '@/lib/constants'
import { PassiveCardWithSuspense } from './PassiveI18n'
import { getEffectiveEgoPassives, getLockedEgoPassives } from '../lib/egoPassiveSelection'

import type { EGOData, Threadspin } from '../types/EGOTypes'

interface EGOPassivesPaneProps {
  /** EGO ID for i18n lookup */
  id: string
  /** Passive lists per threadspin */
  passives: EGOData['passives']
  /** Current threadspin level */
  threadspinLevel: Threadspin
}

/** Passives effective at the current threadspin, plus locked previews. */
export function EGOPassivesPane({ id, passives, threadspinLevel }: EGOPassivesPaneProps) {
  const { t } = useTranslation(['database', 'common'])

  const threadspinIndex = threadspinLevel - 1
  const effectivePassives = getEffectiveEgoPassives(passives.passiveList, threadspinIndex)
  const lockedPassives = getLockedEgoPassives(passives.passiveList, threadspinIndex)

  return (
    <div className="border rounded p-4 space-y-4">
      <div className="space-y-3">
        {effectivePassives.map((passiveId) => (
          <PassiveCardWithSuspense key={passiveId} id={id} passiveId={passiveId} isLocked={false} />
        ))}
        {lockedPassives.map((passiveId) => (
          <PassiveCardWithSuspense key={passiveId} id={id} passiveId={passiveId} isLocked={true} />
        ))}
        {effectivePassives.length === 0 && lockedPassives.length === 0 && (
          <div className={SECTION_STYLES.TEXT.caption}>{t('passive.none', 'No passives')}</div>
        )}
      </div>
    </div>
  )
}
