import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { LabeledPanel } from '@/components/layout/LabeledPanel'
import { getHPIconPath } from '@/shared/assets'
import { KeywordsDisplay } from '@/shared/gameText'
import {
  ResistancePanel,
  SinResistancePanel,
  StaggerPanel,
  StatusPanel,
  TraitsDisplay,
} from '@/shared/unitStatus'
import { useBattleDetailI18n, type BattleI18nConfig } from '../../hooks/useBattleDetailData'
import { phaseTexts, statusView, type StatusView } from '../../lib/battleDetail'
import type { BattlePart, BattlePhase } from '../../schemas/BattleDetailSchemas'

interface BattleInfoPaneProps {
  phase: BattlePhase
  parts: readonly BattlePart[]
  part: BattlePart | undefined
  level: number | undefined
  tier: number
}

function HpPanel({ hp }: { hp: number }) {
  const { t } = useTranslation('database')
  return (
    <LabeledPanel title={t('identity.status')}>
      <div className="flex flex-col items-center gap-1">
        <img src={getHPIconPath()} alt="HP" className="w-6 h-6 object-contain" />
        <span className="text-xs tabular-nums">{hp}</span>
      </div>
    </LabeledPanel>
  )
}

function hasStatus(view: StatusView): boolean {
  return view.hp !== undefined || view.physical !== undefined
}

function StatusBlock({ view }: { view: StatusView }) {
  const { hp, speed, defense, physical, sins, breakSection } = view
  return (
    <div data-testid="battle-status-block" className="space-y-2">
      <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
        {hp !== undefined && speed !== undefined && defense !== undefined && (
          <StatusPanel hp={hp} speed={speed} defense={defense} />
        )}
        {hp !== undefined && (speed === undefined || defense === undefined) && <HpPanel hp={hp} />}
        {physical !== undefined && <ResistancePanel resist={physical} />}
        {hp !== undefined && breakSection !== undefined && (
          <div className="col-span-2 md:col-span-1">
            <StaggerPanel maxHP={hp} staggerThresholds={breakSection} />
          </div>
        )}
      </div>
      {sins !== undefined && <SinResistancePanel resistances={sins} />}
    </div>
  )
}

interface PartLabelProps {
  config: BattleI18nConfig
  id: string
  unitId: string
  phaseId: string
  part: BattlePart
}

function PartName({ config, id, unitId, phaseId, part }: PartLabelProps) {
  const i18n = useBattleDetailI18n(config, id)
  return (
    phaseTexts(i18n, unitId, phaseId)?.parts?.[String(part.id)]?.name ?? (
      <PartTypeName partType={part.partType} />
    )
  )
}

function PartTypeName({ partType }: { partType: string }) {
  const { t } = useTranslation('database')
  return t(`battle.partType.${partType}`)
}

export function BattlePartLabel(props: PartLabelProps) {
  return (
    <Suspense fallback={<PartTypeName partType={props.part.partType} />}>
      <PartName {...props} />
    </Suspense>
  )
}

export function BattleInfoPane({ phase, parts, part, level, tier }: BattleInfoPaneProps) {
  const view = statusView(phase, part, parts, level, tier)

  return (
    <div className="space-y-4">
      {hasStatus(view) && <StatusBlock view={view} />}

      <TraitsDisplay
        traits={[...(phase.unitKeywordList ?? []), ...(phase.associationList ?? [])]}
        table="all"
      />

      <KeywordsDisplay keywords={phase.battleKeywordList} />
    </div>
  )
}
