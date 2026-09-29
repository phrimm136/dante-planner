import { FormattedDescription, StyledSkillName } from '@/shared/gameText'
import { IdentityPassiveCard } from '@/shared/passive'
import { FLAVOR_TEXT_COLOR } from '@/lib/constants'
import type { ImportancePalette } from '@/shared/gameData'
import { useBattleDetailI18n } from '../../hooks/useBattleDetailData'
import type { BattleTextSource } from './BattleSkillCard'

interface PassiveCondition {
  type: string
  values: Record<string, number>
}

interface BattlePassiveCardProps {
  source: BattleTextSource
  passiveId: string
  condition?: PassiveCondition | undefined
  isLocked: boolean
  importanceLevel: number
  palette: ImportancePalette
}

export function BattlePassiveCard({
  source,
  passiveId,
  condition,
  isLocked,
  importanceLevel,
  palette,
}: BattlePassiveCardProps) {
  return (
    <div data-passive-id={passiveId}>
      <IdentityPassiveCard
        nameSlot={<PassiveName source={source} passiveId={passiveId} />}
        descSlot={<PassiveDesc source={source} passiveId={passiveId} />}
        flavorSlot={<PassiveFlavor source={source} passiveId={passiveId} />}
        condition={condition}
        isLocked={isLocked}
        importanceLevel={importanceLevel}
        palette={palette}
      />
    </div>
  )
}

function usePassiveText(source: BattleTextSource, passiveId: string) {
  const i18n = useBattleDetailI18n(source.config, source.id)
  return i18n.phases?.[source.phaseId]?.passives?.[passiveId]
}

function PassiveName({ source, passiveId }: { source: BattleTextSource; passiveId: string }) {
  const text = usePassiveText(source, passiveId)
  return <StyledSkillName name={text?.name ?? passiveId} attributeType="NEUTRAL" />
}

function PassiveDesc({ source, passiveId }: { source: BattleTextSource; passiveId: string }) {
  const text = usePassiveText(source, passiveId)
  return <FormattedDescription text={text?.desc ?? ''} />
}

function PassiveFlavor({ source, passiveId }: { source: BattleTextSource; passiveId: string }) {
  const flavor = usePassiveText(source, passiveId)?.flavor
  if (!flavor) return null
  return (
    <p
      data-testid="passive-flavor"
      className="text-sm italic whitespace-pre-line pt-1"
      style={{ color: FLAVOR_TEXT_COLOR }}
    >
      {flavor}
    </p>
  )
}
