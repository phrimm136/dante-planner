import { Fragment, type CSSProperties, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Separator } from '@/components/ui/separator'
import { GAME_TEXT_CLASS, PASSIVE_TAB_UNITS } from '@/lib/constants'
import { clientLength, cn } from '@/lib/utils'
import { getEffectiveEgoPassives, getLockedEgoPassives } from '../lib/egoPassiveSelection'
import { PassivePlaceholder } from './PassivePlaceholder'

import type { PassiveId, Threadspin } from '@/shared/gameData'

interface EGOPassiveLists {
  passiveList: PassiveId[][]
}

interface EGOPassivesPaneProps {
  passives: EGOPassiveLists
  threadspinLevel: Threadspin
  renderPassive: (passiveId: PassiveId, isLocked: boolean) => ReactNode
}

const PASSIVE_TAB_STYLE: CSSProperties = {
  padding: `${clientLength(PASSIVE_TAB_UNITS.TOP)} 0 ${clientLength(PASSIVE_TAB_UNITS.BOTTOM)}`,
}

const PASSIVE_SEPARATOR_STYLE: CSSProperties = {
  height: clientLength(PASSIVE_TAB_UNITS.LINE),
  marginBlock: clientLength((PASSIVE_TAB_UNITS.GAP - PASSIVE_TAB_UNITS.LINE) / 2),
}

interface ShownPassive<Id extends string> {
  id: Id
  isLocked: boolean
}

export function PassiveTab({ children }: { children: ReactNode }) {
  return (
    <div
      className={cn('importance-pane border rounded', GAME_TEXT_CLASS)}
      style={PASSIVE_TAB_STYLE}
    >
      {children}
    </div>
  )
}

export function PassiveRun<Id extends string>({
  passives,
  renderPassive,
}: {
  passives: readonly ShownPassive<Id>[]
  renderPassive: (passiveId: Id, isLocked: boolean) => ReactNode
}) {
  return passives.map(({ id, isLocked }, index) => (
    <Fragment key={id}>
      {index > 0 && <Separator data-slot="passive-separator" style={PASSIVE_SEPARATOR_STYLE} />}
      {renderPassive(id, isLocked)}
    </Fragment>
  ))
}

export function EGOPassivesPane({
  passives,
  threadspinLevel,
  renderPassive,
}: EGOPassivesPaneProps) {
  const { t } = useTranslation(['database', 'common'])

  const threadspinIndex = threadspinLevel - 1
  const shown = [
    ...getEffectiveEgoPassives(passives.passiveList, threadspinIndex).map((id) => ({
      id,
      isLocked: false,
    })),
    ...getLockedEgoPassives(passives.passiveList, threadspinIndex).map((id) => ({
      id,
      isLocked: true,
    })),
  ]

  return (
    <PassiveTab>
      <PassiveRun passives={shown} renderPassive={renderPassive} />
      {shown.length === 0 && (
        <PassivePlaceholder>{t('passive.none', 'No passives')}</PassivePlaceholder>
      )}
    </PassiveTab>
  )
}
