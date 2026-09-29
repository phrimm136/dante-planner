import { Suspense, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { LabeledPanel } from '@/components/layout/LabeledPanel'
import { getHPIconPath } from '@/shared/assets'
import { KeywordsDisplay } from '@/shared/gameText'
import { useThemePackListI18n } from '@/shared/themePack'
import {
  ResistancePanel,
  SinResistancePanel,
  StaggerPanel,
  StatusPanel,
  TraitsDisplay,
} from '@/shared/unitStatus'
import { useBattleDetailI18n, type BattleI18nConfig } from '../../hooks/useBattleDetailData'
import { useBattleFacetNames } from '../../hooks/useBattleFacetNames'
import { useBattleStageListI18n, useBattleStageListSpec } from '../../hooks/useBattleStageData'
import {
  defenseAtLevel,
  partStatus,
  phaseStatus,
  resistances,
  speedAtTier,
  statAtLevel,
  type UnitStatus,
} from '../../lib/battleDetail'
import { groupEngagements } from '../../lib/engagementLabels'
import type { Translate } from '../../lib/enemy/enemyFacetOptions'
import type { BattleEngagement, BattlePart, BattlePhase } from '../../schemas/BattleDetailSchemas'

interface BattleInfoPaneProps {
  config: BattleI18nConfig
  id: string
  phase: BattlePhase
  part: BattlePart | undefined
  level: number | undefined
  tier: number
  engagements: readonly BattleEngagement[]
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
}: {
  status: UnitStatus
  level: number | undefined
  tier: number
  title?: ReactNode
}) {
  const hp =
    status.hp === undefined || level === undefined ? undefined : statAtLevel(status.hp, level)
  const minSpeed = speedAtTier(status.minSpeedList, tier)
  const maxSpeed = speedAtTier(status.maxSpeedList, tier)
  const resist = status.resistInfo === undefined ? undefined : resistances(status.resistInfo)

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
        {hp !== undefined && status.breakSection !== undefined && (
          <div className="col-span-2 md:col-span-1">
            <StaggerPanel maxHP={hp} staggerThresholds={status.breakSection.sectionList} />
          </div>
        )}
      </div>
      {resist !== undefined && <SinResistancePanel resistances={resist.attributes} />}
    </div>
  )
}

function PartName({
  config,
  id,
  phaseId,
  part,
}: {
  config: BattleI18nConfig
  id: string
  phaseId: string
  part: BattlePart
}) {
  const i18n = useBattleDetailI18n(config, id)
  return (
    i18n.phases?.[phaseId]?.parts?.[String(part.id)]?.name ?? (
      <PartTypeName partType={part.partType} />
    )
  )
}

function PartTypeName({ partType }: { partType: string }) {
  const { t } = useTranslation('database')
  return t(`battle.partType.${partType}`)
}

export function BattlePartLabel({
  config,
  id,
  phaseId,
  part,
}: {
  config: BattleI18nConfig
  id: string
  phaseId: string
  part: BattlePart
}) {
  return (
    <Suspense fallback={<PartTypeName partType={part.partType} />}>
      <PartName config={config} id={id} phaseId={phaseId} part={part} />
    </Suspense>
  )
}

function EngagementsPanel({ engagements }: { engagements: readonly BattleEngagement[] }) {
  const { t } = useTranslation('database')
  const translate: Translate = (key, options) => t(key, options)
  const stages = useBattleStageListSpec()
  const texts = useBattleStageListI18n()
  const facetNames = useBattleFacetNames()
  const themePacks = useThemePackListI18n()
  const groups = groupEngagements(engagements, { stages, texts, facetNames, themePacks }, translate)

  return (
    <LabeledPanel title={t('battle.detail.engagements')} titleAlign="left">
      <div data-testid="battle-engagements" className="max-h-64 space-y-2 overflow-y-auto text-xs">
        {groups.map((group) => (
          <div key={group.battleCase} data-case={group.battleCase} className="space-y-0.5">
            <div className="font-semibold">{t(`battle.case.${group.battleCase}`)}</div>
            <ul className="space-y-0.5">
              {group.labels.map((label) => (
                <li key={label} className="line-clamp-1">
                  {label}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </LabeledPanel>
  )
}

export function BattleInfoPane({
  config,
  id,
  phase,
  part,
  level,
  tier,
  engagements,
}: BattleInfoPaneProps) {
  const phaseId = String(phase.id)
  const partBlocks = (part === undefined ? (phase.parts ?? []) : [part]).filter((each) =>
    hasStatus(partStatus(each)),
  )

  return (
    <div className="space-y-4">
      {part === undefined && <StatusBlock status={phaseStatus(phase)} level={level} tier={tier} />}
      {partBlocks.map((each) => (
        <StatusBlock
          key={each.id}
          status={partStatus(each)}
          level={level}
          tier={tier}
          title={<BattlePartLabel config={config} id={id} phaseId={phaseId} part={each} />}
        />
      ))}

      <TraitsDisplay traits={phase.unitKeywordList ?? []} />

      <KeywordsDisplay keywords={phase.battleKeywordList} />

      {engagements.length > 0 && (
        <Suspense
          fallback={
            <LabeledPanel>
              <TextSkeleton size="xs" lines={3} width="full" />
            </LabeledPanel>
          }
        >
          <EngagementsPanel engagements={engagements} />
        </Suspense>
      )}
    </div>
  )
}
