import { useEGODetailI18n } from '../hooks/useEGODetailData'
import { FormattedDescription } from '@/shared/gameText'
import { NamePlate, type NamePlateImportance } from '@/shared/gameText'
import { EGOPassiveCard, passiveBoxLayout } from '@/shared/passive'
import type { PassiveId } from '@/shared/gameData'

interface PassiveCardWithSuspenseProps {
  id: string
  passiveId: PassiveId
  isLocked: boolean
}

export function PassiveCardWithSuspense({ id, passiveId, isLocked }: PassiveCardWithSuspenseProps) {
  return (
    <EGOPassiveCard
      renderName={(importance) => (
        <PassiveNameContent id={id} passiveId={passiveId} importance={importance} />
      )}
      descSlot={<PassiveDescContent id={id} passiveId={passiveId} />}
      flavorSlot={<PassiveFlavorContent id={id} passiveId={passiveId} />}
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
  const i18n = useEGODetailI18n(id)
  const passive = i18n.passives[passiveId]
  return (
    <NamePlate
      kind="passive"
      name={passive?.name || passiveId}
      attributeType="NEUTRAL"
      importance={importance}
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
      style={passiveBoxLayout().flavor}
    >
      {flavor}
    </p>
  )
}

export { PassiveCardWithSuspense as PassiveCardI18n }
