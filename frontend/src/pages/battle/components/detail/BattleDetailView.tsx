import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { DetailSection } from '@/components/layout/DetailSection'
import { EntityDetailLayout } from '@/components/layout/EntityDetailLayout'
import type { ImportancePalette } from '@/shared/gameData'
import { SkillList } from '@/shared/skill'
import type { BattleI18nConfig } from '../../hooks/useBattleDetailData'
import {
  ALL_PARTS,
  phaseTier,
  rowParts,
  ownSources,
  rowUnitId,
  selectedPart,
  selectorParts,
  visibleSkills,
  type PartSelection,
} from '../../lib/battleDetail'
import type { BattleCase, BattleFamily } from '../../lib/battleVocabulary'
import { defaultRowIndex, firstMapName, groupSelectorRows } from '../../lib/engagementRows'
import type { BattleKit, EngagementRow } from '../../schemas/BattleDetailSchemas'
import { BattleDetailHeader } from './BattleDetailHeader'
import { BattleEgoSection } from './BattleEgoSection'
import { BattleEngagementRows } from './BattleEngagementRows'
import { BattleInfoPane, BattlePartLabel } from './BattleInfoPane'
import { BattlePassivesPane } from './BattlePassivesPane'
import { BattleSanityPane } from './BattleSanityPane'
import { BattleSelectorRow, type SelectorOption } from './BattleSelectorRow'
import { BattleSkillCard } from './BattleSkillCard'
import { BattleStageEffects } from './BattleStageEffects'

export interface BattleGroup {
  units: Record<string, BattleKit>
  engagements: readonly EngagementRow[]
}

export interface BattleDetailViewProps {
  family: BattleFamily
  id: string
  config: BattleI18nConfig
  group: BattleGroup
  palette: ImportancePalette
  arrivalMode: BattleCase
}

interface Selection {
  rowIndex: number | undefined
  phaseIndex: number
  part: PartSelection
}

function kitOf(group: BattleGroup, unitId: string): BattleKit {
  const kit = group.units[unitId] ?? Object.values(group.units)[0]
  if (kit === undefined) throw new Error('battle group has no unit')
  return kit
}

export function BattleDetailView({
  family,
  id,
  config,
  group,
  palette,
  arrivalMode,
}: BattleDetailViewProps) {
  const { t } = useTranslation('database')
  const rowGroups = groupSelectorRows(group.engagements)
  const [selection, setSelection] = useState<Selection>(() => ({
    rowIndex: defaultRowIndex(rowGroups, arrivalMode),
    phaseIndex: 0,
    part: ALL_PARTS,
  }))

  const row = selection.rowIndex === undefined ? undefined : group.engagements[selection.rowIndex]
  const unitId = rowUnitId(Object.keys(group.units), id, row)
  const kit = kitOf(group, unitId)
  const phase = kit.phases[selection.phaseIndex] ?? kit.phases[0]
  const phaseId = String(phase.id)
  const parts = rowParts(phase, row)
  const part = selectedPart(parts, selection.part)
  const level = row?.level ?? kit.level
  const tier = phaseTier(phase, kit.uptie)
  const source = { config, id, unitId, phaseId }
  const imageOwnerId = phase.identitySource === undefined ? undefined : String(phase.identitySource)
  const choices = selectorParts(parts)
  const partOptions: SelectorOption<PartSelection>[] =
    choices.length === 0
      ? []
      : [
          { value: ALL_PARTS, label: t('battle.detail.allParts') },
          ...choices.map((each) => ({
            value: each.id,
            label: (
              <BattlePartLabel
                config={config}
                id={id}
                unitId={unitId}
                phaseId={phaseId}
                part={each}
              />
            ),
          })),
        ]
  const egoList = phase.egoList ?? []
  const importancePosition =
    phase.ownerType === 'ABNORMALITY' && part === undefined ? 'base' : 'unlock'

  const selector = (
    <>
      <BattleSelectorRow
        engagementRows={
          <BattleEngagementRows
            config={config}
            id={id}
            units={group.units}
            groups={rowGroups}
            selected={selection.rowIndex}
            onSelect={(rowIndex) => {
              setSelection({ rowIndex, phaseIndex: 0, part: ALL_PARTS })
            }}
          />
        }
        phaseCount={kit.phases.length}
        phaseIndex={selection.phaseIndex}
        onPhaseChange={(phaseIndex) => {
          setSelection((previous) => ({ ...previous, phaseIndex, part: ALL_PARTS }))
        }}
        partOptions={partOptions}
        part={part === undefined ? ALL_PARTS : part.id}
        onPartChange={(next) => {
          setSelection((previous) => ({ ...previous, part: next }))
        }}
      />
      {row !== undefined && (
        <BattleStageEffects
          source={source}
          row={row}
          own={ownSources(unitId, kit.phases)}
          phaseIds={kit.phases.map((each) => String(each.id))}
        />
      )}
    </>
  )

  return (
    <EntityDetailLayout
      header={
        <BattleDetailHeader
          family={family}
          id={id}
          unitId={unitId}
          config={config}
          phase={phase}
          classType={kit.badge?.classType}
          hazardGrade={kit.badge?.hazardGrade}
          mapName={firstMapName(rowGroups)}
        />
      }
      metadata={
        <BattleInfoPane
          config={config}
          id={id}
          unitId={unitId}
          phase={phase}
          parts={parts}
          part={part}
          level={level}
          tier={tier}
        />
      }
      selector={selector}
    >
      <DetailSection title={t('section.skills')}>
        <SkillList
          skills={visibleSkills(phase, parts, part)}
          renderSkill={({ id: skillId, entry }) => (
            <BattleSkillCard
              source={source}
              imageOwnerId={imageOwnerId}
              skillId={skillId}
              entry={entry}
              tier={tier}
              palette={palette}
              importancePosition={importancePosition}
            />
          )}
        />
      </DetailSection>
      <DetailSection title={t('section.passives')}>
        <BattlePassivesPane
          source={source}
          phase={phase}
          parts={parts}
          part={part}
          tier={tier}
          palette={palette}
        />
      </DetailSection>
      {egoList.length > 0 && (
        <DetailSection title={t('section.ego')}>
          <BattleEgoSection egoList={egoList} palette={palette} />
        </DetailSection>
      )}
      {phase.sanity.hasSanity && (
        <DetailSection title={t('section.sanity')}>
          <BattleSanityPane source={source} sanity={phase.sanity} />
        </DetailSection>
      )}
    </EntityDetailLayout>
  )
}
