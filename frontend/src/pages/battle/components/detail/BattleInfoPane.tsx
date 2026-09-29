import { Suspense, type ReactNode } from 'react'
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
import {
  defenseAtLevel,
  partStatus,
  phaseStatus,
  phaseTexts,
  resistances,
  speedAtTier,
  statAtLevel,
  type UnitStatus,
} from '../../lib/battleDetail'
import type { BattlePart, BattlePhase } from '../../schemas/BattleDetailSchemas'

interface BattleInfoPaneProps {
  config: BattleI18nConfig
  id: string
  unitId: string
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

function hasStatus(status: UnitStatus): boolean {
  return status.hp !== undefined || status.resistInfo !== undefined
}

function StatusBlock({
  status,
  level,
  tier,
  title,
  summary = false,
}: {
  status: UnitStatus
  level: number | undefined
  tier: number
  title?: ReactNode
  summary?: boolean
}) {
  const hp =
    status.hp === undefined || level === undefined ? undefined : statAtLevel(status.hp, level)
  const minSpeed = speedAtTier(status.minSpeedList, tier)
  const maxSpeed = speedAtTier(status.maxSpeedList, tier)
  const resist =
    summary || status.resistInfo === undefined ? undefined : resistances(status.resistInfo)

  const defCorrection = status.defCorrection
  const hasFullStatus =
    minSpeed !== undefined && maxSpeed !== undefined && defCorrection !== undefined

  return (
    <div data-testid="battle-status-block" className="space-y-2">
      {title !== undefined && <div className="text-sm font-semibold">{title}</div>}
      <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
        {hp !== undefined && level !== undefined && hasFullStatus && (
          <StatusPanel
            hp={hp}
            minSpeed={minSpeed}
            maxSpeed={maxSpeed}
            defLevel={defenseAtLevel(level, defCorrection)}
            defCorrection={defCorrection}
          />
        )}
        {hp !== undefined && !hasFullStatus && <HpPanel hp={hp} />}
        {resist !== undefined && (
          <ResistancePanel slash={resist.slash} pierce={resist.pierce} blunt={resist.blunt} />
        )}
        {!summary && hp !== undefined && status.breakSection !== undefined && (
          <div className="col-span-2 md:col-span-1">
            <StaggerPanel maxHP={hp} staggerThresholds={status.breakSection.sectionList} />
          </div>
        )}
      </div>
      {resist !== undefined && <SinResistancePanel resistances={resist.attributes} />}
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

function PartHpLines({
  parts,
  level,
  label,
}: {
  parts: readonly BattlePart[]
  level: number | undefined
  label: (part: BattlePart) => ReactNode
}) {
  return (
    <ul className="space-y-1 text-sm">
      {parts.map((each) => (
        <li
          key={each.id}
          data-testid="battle-part-hp"
          className="flex items-center justify-between gap-2 rounded border border-border px-3 py-1"
        >
          <span>{label(each)}</span>
          {each.hp !== undefined && level !== undefined && (
            <span className="flex items-center gap-1 tabular-nums">
              <img src={getHPIconPath()} alt="HP" className="h-4 w-4 object-contain" />
              {statAtLevel(each.hp, level)}
            </span>
          )}
        </li>
      ))}
    </ul>
  )
}

export function BattleInfoPane({
  config,
  id,
  unitId,
  phase,
  parts,
  part,
  level,
  tier,
}: BattleInfoPaneProps) {
  const phaseId = String(phase.id)
  const summary = part === undefined && parts.length >= 2
  const partBlocks = summary
    ? []
    : (part === undefined ? parts : [part]).filter((each) => hasStatus(partStatus(each)))
  const partLabel = (each: BattlePart) => (
    <BattlePartLabel config={config} id={id} unitId={unitId} phaseId={phaseId} part={each} />
  )

  return (
    <div className="space-y-4">
      {part === undefined && (
        <StatusBlock status={phaseStatus(phase)} level={level} tier={tier} summary={summary} />
      )}
      {summary && <PartHpLines parts={parts} level={level} label={partLabel} />}
      {partBlocks.map((each) => (
        <StatusBlock
          key={each.id}
          status={partStatus(each)}
          level={level}
          tier={tier}
          title={partLabel(each)}
        />
      ))}

      <TraitsDisplay
        traits={[...(phase.unitKeywordList ?? []), ...(phase.associationList ?? [])]}
        table="all"
      />

      <KeywordsDisplay keywords={phase.battleKeywordList} />
    </div>
  )
}
