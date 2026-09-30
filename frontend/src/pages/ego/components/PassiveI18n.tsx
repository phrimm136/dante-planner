import { useEGODetailI18n } from '../hooks/useEGODetailData'
import { FormattedDescription } from '@/shared/gameText'
import { StyledSkillName } from '@/shared/gameText'
import { EGOPassiveCard, passiveBoxLayout } from '@/shared/passive'
import { FLAVOR_TEXT_COLOR } from '@/lib/constants'
import type { PassiveId } from '@/shared/gameData'

interface PassiveCardWithSuspenseProps {
  id: string
  passiveId: PassiveId
  isLocked: boolean
}

export function PassiveCardWithSuspense({ id, passiveId, isLocked }: PassiveCardWithSuspenseProps) {
  return (
    <EGOPassiveCard
      nameSlot={<PassiveNameContent id={id} passiveId={passiveId} />}
      descSlot={<PassiveDescContent id={id} passiveId={passiveId} />}
      flavorSlot={<PassiveFlavorContent id={id} passiveId={passiveId} />}
      isLocked={isLocked}
      importanceLevel={0}
      palette="player"
    />
  )
}

function PassiveNameContent({ id, passiveId }: { id: string; passiveId: PassiveId }) {
  const i18n = useEGODetailI18n(id)
  const passive = i18n.passives[passiveId]
  return (
    <StyledSkillName
      name={passive?.name || passiveId}
      attributeType="NEUTRAL"
      plate={passiveBoxLayout().plate}
    />
  )
}

function PassiveDescContent({ id, passiveId }: { id: string; passiveId: PassiveId }) {
  const i18n = useEGODetailI18n(id)
  const passive = i18n.passives[passiveId]
  return <FormattedDescription text={passive?.desc ?? ''} />
}

function PassiveFlavorContent({ id, passiveId }: { id: string; passiveId: PassiveId }) {
  const i18n = useEGODetailI18n(id)
  const passive = i18n.passives[passiveId]
  const flavor = passive?.flavor
  if (!flavor) return null
  return (
    <p
      data-testid="passive-flavor"
      className="italic whitespace-pre-line"
      style={{ ...passiveBoxLayout().flavor, color: FLAVOR_TEXT_COLOR }}
    >
      {flavor}
    </p>
  )
}

export { PassiveCardWithSuspense as PassiveCardI18n }
