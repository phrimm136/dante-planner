import { Suspense, useState } from 'react'
import { useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { DetailSection } from '@/components/layout/DetailSection'
import { EntityDetailLayout } from '@/components/layout/EntityDetailLayout'
import type { ImportancePalette } from '@/shared/gameData'
import { SkillList } from '@/shared/skill'
import { useBattleDetailI18n, type BattleI18nConfig } from '../../hooks/useBattleDetailData'
import {
  ALL_PARTS,
  headerMapName,
  phaseTier,
  phaseTexts,
  rowParts,
  rowUnitId,
  selectedPart,
  visibleSkills,
  type PartSelection,
} from '../../lib/battleDetail'
import { activePhase, formsLocked, rowFormKeys, selectedFormKey } from '../../lib/battleForms'
import { defaultGrowthInputs, growthState, grownPhase } from '../../lib/battleGrowth'
import type { BattleCase, BattleFamily, BattleForm } from '../../lib/battleVocabulary'
import { defaultRowIndex, groupSelectorRows } from '../../lib/engagementRows'
import type { BattleKit, EngagementRow } from '../../schemas/BattleDetailSchemas'
import { BattleCoUnits } from '../coUnits/BattleCoUnits'
import { BattleDetailHeader } from './BattleDetailHeader'
import { BattleEgoSection } from './BattleEgoSection'
import { BattleEngagementRows } from './BattleEngagementRows'
import { BattleGrowthInputs } from './BattleGrowthInputs'
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
  formKey: BattleForm | undefined
}

interface FormLabelProps {
  config: BattleI18nConfig
  id: string
  unitId: string
  phaseId: string
  form: BattleForm
}

const FORM_FALLBACK_KEYS = {
  base: 'battle.detail.baseForm',
  shoes: 'battle.detail.formFallback.shoes',
  cape: 'battle.detail.formFallback.cape',
} as const satisfies Record<BattleForm, string>

function BaseFormName({ form }: { form: BattleForm }) {
  const { t } = useTranslation('database')
  return t(FORM_FALLBACK_KEYS[form])
}

function FormName({ config, id, unitId, phaseId, form }: FormLabelProps) {
  const i18n = useBattleDetailI18n(config, id)
  return phaseTexts(i18n, unitId, phaseId)?.formNames?.[form] ?? <BaseFormName form={form} />
}

function BattleFormLabel(props: FormLabelProps) {
  return (
    <Suspense fallback={<BaseFormName form={props.form} />}>
      <FormName {...props} />
    </Suspense>
  )
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
  const { stage } = useSearch({ strict: false })
  const rowGroups = groupSelectorRows(group.engagements)
  const [selection, setSelection] = useState<Selection>(() => ({
    rowIndex: defaultRowIndex(rowGroups, arrivalMode, stage),
    phaseIndex: 0,
    part: ALL_PARTS,
    formKey: undefined,
  }))
  const [growthInputs, setGrowthInputs] = useState<Record<string, number>>({})

  const row = selection.rowIndex === undefined ? undefined : group.engagements[selection.rowIndex]
  const unitId = rowUnitId(Object.keys(group.units), id, row)
  const kit = kitOf(group, unitId)
  const formKey = selectedFormKey(row, selection.formKey)
  const basePhase = activePhase(kit, row, selection.phaseIndex, formKey)
  const { growth } = basePhase
  const inputs = growth === undefined ? {} : { ...defaultGrowthInputs(growth), ...growthInputs }
  const phase = grownPhase(basePhase, inputs)
  const phaseId = String(phase.id)
  const parts = rowParts(phase, row)
  const part = selectedPart(parts, selection.part)
  const baseLevel = row?.level ?? kit.level
  const grown =
    growth === undefined || baseLevel === undefined
      ? undefined
      : growthState(growth, inputs, baseLevel)
  const level = grown?.level ?? baseLevel
  const tier = phaseTier(phase, kit.uptie)
  const source = { config, id, unitId, phaseId }
  const formOptions: SelectorOption<BattleForm>[] = rowFormKeys(row).map((form) => ({
    value: form,
    label: (
      <BattleFormLabel config={config} id={id} unitId={unitId} phaseId={phaseId} form={form} />
    ),
  }))
  const imageOwnerId = phase.identitySource === undefined ? undefined : String(phase.identitySource)
  const partOptions: SelectorOption<PartSelection>[] =
    parts.length === 0
      ? []
      : [
          { value: ALL_PARTS, label: t('battle.detail.allParts') },
          ...parts.map((each) => ({
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
            groups={rowGroups}
            selected={selection.rowIndex}
            onSelect={(rowIndex) => {
              setSelection({ rowIndex, phaseIndex: 0, part: ALL_PARTS, formKey: undefined })
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
        forms={{
          options: formOptions,
          selected: formKey,
          locked: formsLocked(row),
          onSelect: (next) => {
            setSelection((previous) => ({ ...previous, formKey: next, part: ALL_PARTS }))
          },
        }}
        growth={
          growth !== undefined && (
            <BattleGrowthInputs
              source={source}
              growth={growth}
              inputs={inputs}
              onChange={(track, value) => {
                setGrowthInputs((previous) => ({ ...previous, [track]: value }))
              }}
            />
          )
        }
      />
      {row !== undefined && <BattleCoUnits row={row} family={family} id={id} />}
      {row !== undefined && <BattleStageEffects row={row} startBuffs={phase.startBuffs ?? []} />}
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
          riskPlate={kit.badge}
          mapName={headerMapName(row, phase)}
        />
      }
      metadata={
        <BattleInfoPane phase={phase} parts={parts} part={part} level={level} tier={tier} />
      }
      selector={selector}
    >
      <DetailSection title={t('section.skills')}>
        <SkillList
          skills={visibleSkills(phase, parts, part, row)}
          renderSkill={({ id: skillId, entry }) => (
            <BattleSkillCard
              source={source}
              imageOwnerId={imageOwnerId}
              skillId={skillId}
              entry={entry}
              tier={tier}
              palette={palette}
              importancePosition={importancePosition}
              unitLevel={level}
              skillLevelAdders={grown?.skillLevelAdders}
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
          <BattleEgoSection egoList={egoList} unitLevel={level} palette={palette} />
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
