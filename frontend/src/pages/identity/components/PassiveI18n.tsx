import { useTranslation } from 'react-i18next'

import { useIdentityDetailI18n } from '../hooks/useIdentityDetailData'
import { FormattedDescription } from '@/shared/gameText'
import { NamePlate, StyledSkillName, type NamePlateImportance } from '@/shared/gameText'
import { IdentityPassiveCard, passiveBoxLayout } from '@/shared/passive'
import {
  getAffinityIconPath,
  getIdentityPassiveCountIconPath,
  getLockIconPath,
} from '@/shared/assets'
import { cn } from '@/lib/utils'
import { FLAVOR_TEXT_COLOR, SECTION_STYLES } from '@/lib/constants'
import type { PassiveId } from '@/shared/gameData'

interface PassiveCondition {
  type: string
  values: Record<string, number>
}

interface PassiveCardProps {
  name: string
  desc: string
  /** Passive lore line; mirrors in-game `[Text]SkillInfoFlavor`-style TMP shown beneath the description */
  flavor?: string
  condition?: PassiveCondition
  isLocked: boolean
}

interface PassiveCardWithSuspenseProps {
  id: string
  passiveId: PassiveId
  condition?: PassiveCondition | undefined
  isLocked: boolean
}

export function PassiveCard({ name, desc, flavor, condition, isLocked }: PassiveCardProps) {
  const { t } = useTranslation(['database', 'common'])

  return (
    <div className={cn('relative space-y-1', isLocked && 'opacity-50')}>
      <div className={SECTION_STYLES.LAYOUT.row}>
        <StyledSkillName name={name} attributeType="NEUTRAL" />
      </div>
      {isLocked && (
        <img
          src={getLockIconPath()}
          alt=""
          className="absolute right-0 bottom-0 -z-10 h-20 brightness-20"
        />
      )}
      {condition && (
        <div className="flex items-center gap-3 text-md ml-1">
          {Object.entries(condition.values).map(([affinity, count]) => (
            <span key={affinity} className={SECTION_STYLES.LAYOUT.rowTight}>
              <img src={getAffinityIconPath(affinity)} alt={affinity} className="w-8 h-8" />
              <img src={getIdentityPassiveCountIconPath()} alt="x" className="w-4 h-4" />
              <span>{count}</span>
            </span>
          ))}
          <span>{t(`passive.${condition.type.toLowerCase()}`)}</span>
        </div>
      )}
      <div className="text-sm">
        <FormattedDescription text={desc} />
      </div>
      {flavor && (
        <p
          data-testid="passive-flavor"
          className="text-sm italic whitespace-pre-line pt-1"
          style={{ color: FLAVOR_TEXT_COLOR }}
        >
          {flavor}
        </p>
      )}
    </div>
  )
}

export function PassiveCardWithSuspense({
  id,
  passiveId,
  condition,
  isLocked,
}: PassiveCardWithSuspenseProps) {
  return (
    <IdentityPassiveCard
      renderName={(importance) => (
        <PassiveNameContent id={id} passiveId={passiveId} importance={importance} />
      )}
      descSlot={<PassiveDescContent id={id} passiveId={passiveId} />}
      flavorSlot={<PassiveFlavorContent id={id} passiveId={passiveId} />}
      condition={condition}
      isLocked={isLocked}
      importanceLevel={0}
      palette="player"
    />
  )
}

function PassiveNameContent({
  id,
  passiveId,
  importance,
}: {
  id: string
  passiveId: PassiveId
  importance: NamePlateImportance
}) {
  const i18n = useIdentityDetailI18n(id)
  const passiveI18n = i18n.passives[passiveId]
  return (
    <NamePlate
      kind="passive"
      name={passiveI18n?.name ?? ''}
      attributeType="NEUTRAL"
      importance={importance}
    />
  )
}

function PassiveDescContent({ id, passiveId }: { id: string; passiveId: PassiveId }) {
  const i18n = useIdentityDetailI18n(id)
  const passiveI18n = i18n.passives[passiveId]
  return <FormattedDescription text={passiveI18n?.desc ?? ''} />
}

function PassiveFlavorContent({ id, passiveId }: { id: string; passiveId: PassiveId }) {
  const i18n = useIdentityDetailI18n(id)
  const passiveI18n = i18n.passives[passiveId]
  const flavor = passiveI18n?.flavor
  if (!flavor) return null
  return (
    <p
      data-testid="passive-flavor"
      className="italic whitespace-pre-line"
      style={passiveBoxLayout().flavor}
    >
      {flavor}
    </p>
  )
}

export { PassiveCardWithSuspense as PassiveCardI18n }
