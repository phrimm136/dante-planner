import { FormattedDescription, NamePlate, type NamePlateImportance } from '@/shared/gameText'
import { IdentityPassiveCard, passiveBoxLayout } from '@/shared/passive'
import type { ImportancePalette } from '@/shared/gameData'
import { useBattleDetailI18n } from '../../hooks/useBattleDetailData'
import { phaseTexts } from '../../lib/battleDetail'
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
        renderName={(importance) => (
          <PassiveName source={source} passiveId={passiveId} importance={importance} />
        )}
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
  return phaseTexts(i18n, source.unitId, source.phaseId)?.passives?.[passiveId]
}

function PassiveName({
  source,
  passiveId,
  importance,
}: {
  source: BattleTextSource
  passiveId: string
  importance: NamePlateImportance
}) {
  const text = usePassiveText(source, passiveId)
  return (
    <NamePlate
      kind="passive"
      name={text?.name ?? passiveId}
      attributeType="NEUTRAL"
      importance={importance}
    />
  )
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
      className="italic whitespace-pre-line"
      style={passiveBoxLayout().flavor}
    >
      {flavor}
    </p>
  )
}
