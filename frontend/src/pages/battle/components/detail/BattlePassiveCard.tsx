import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { PANEL_TEXT_NODES, UNIT_PANEL_COLORS } from '@/lib/constants'
import { FormattedDescription, NamePlate, type NamePlateImportance } from '@/shared/gameText'
import { IdentityPassiveCard, passiveBoxLayout } from '@/shared/passive'
import { textBlockStyle } from '@/shared/skill'
import type { ImportancePalette } from '@/shared/gameData'
import { useBattleDetailI18n } from '../../hooks/useBattleDetailData'
import { acquireConditionText, phaseTexts } from '../../lib/battleDetail'
import type { BattleUnlock } from '../../schemas/BattleDetailSchemas'
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
  unlock?: BattleUnlock | undefined
}

const UNLOCK_STYLE = textBlockStyle(PANEL_TEXT_NODES.PASSIVE_INFO, UNIT_PANEL_COLORS.PASSIVE_INFO)

export function BattlePassiveCard({
  source,
  passiveId,
  condition,
  isLocked,
  importanceLevel,
  palette,
  unlock,
}: BattlePassiveCardProps) {
  return (
    <div data-passive-id={passiveId} className="relative">
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
      {unlock !== undefined && (
        <Suspense fallback={null}>
          <PassiveUnlock source={source} unlock={unlock} />
        </Suspense>
      )}
    </div>
  )
}

function PassiveUnlock({ source, unlock }: { source: BattleTextSource; unlock: BattleUnlock }) {
  const { t } = useTranslation('database')
  const i18n = useBattleDetailI18n(source.config, source.id)
  const texts = phaseTexts(i18n, source.unitId, source.phaseId)
  const conditions = acquireConditionText(
    [unlock],
    texts?.conditionNames,
    { and: t('battle.detail.acquireAnd'), or: t('battle.detail.acquireOr') },
    texts?.conditionGrades,
  )
  const { right, bottom } = passiveBoxLayout().lock
  return (
    <p
      data-slot="passive-unlock"
      className="absolute text-sm"
      style={{ ...UNLOCK_STYLE, right, bottom }}
    >
      {t('battle.detail.unlock', { conditions })}
    </p>
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
