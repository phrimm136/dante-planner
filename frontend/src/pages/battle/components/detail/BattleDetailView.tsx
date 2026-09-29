import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { DetailSection } from '@/components/layout/DetailSection'
import { EntityDetailLayout } from '@/components/layout/EntityDetailLayout'
import type { ImportancePalette } from '@/shared/gameData'
import type { BattleI18nConfig } from '../../hooks/useBattleDetailData'
import {
  ALL_PARTS,
  defaultEngagementLevel,
  engagementLevels,
  phaseTier,
  selectedPart,
  selectorParts,
  visibleSkills,
  type PartSelection,
} from '../../lib/battleDetail'
import type { BattleCase, BattleFamily } from '../../lib/battleVocabulary'
import type { BattleEngagement, BattlePhase, EnemyData } from '../../schemas/BattleDetailSchemas'
import { BattleDetailHeader } from './BattleDetailHeader'
import { BattleEgoSection } from './BattleEgoSection'
import { BattleInfoPane, BattlePartLabel } from './BattleInfoPane'
import { BattlePassivesPane } from './BattlePassivesPane'
import { BattleSanityPane } from './BattleSanityPane'
import { BattleSelectorRow, type SelectorOption } from './BattleSelectorRow'
import { BattleSkillCard } from './BattleSkillCard'

export interface BattleDetailViewProps {
  family: BattleFamily
  id: string
  config: BattleI18nConfig
  engagements: readonly BattleEngagement[]
  phases: readonly [BattlePhase, ...BattlePhase[]]
  palette: ImportancePalette
  arrivalCases: ReadonlySet<BattleCase>
  fallbackLevel: number | undefined
  uptie: number | undefined
  badge?: EnemyData['badge']
}

export function BattleDetailView({
  family,
  id,
  config,
  engagements,
  phases,
  palette,
  arrivalCases,
  fallbackLevel,
  uptie,
  badge,
}: BattleDetailViewProps) {
  const { t } = useTranslation('database')
  const [selection, setSelection] = useState<{ phaseIndex: number; part: PartSelection }>({
    phaseIndex: 0,
    part: ALL_PARTS,
  })
  const [level, setLevel] = useState(() =>
    defaultEngagementLevel(engagements, arrivalCases, fallbackLevel),
  )

  const phase = phases[selection.phaseIndex] ?? phases[0]
  const phaseId = String(phase.id)
  const part = selectedPart(phase, selection.part)
  const tier = phaseTier(phase, uptie)
  const source = { config, id, phaseId }
  const imageOwnerId = phase.identitySource === undefined ? undefined : String(phase.identitySource)
  const parts = selectorParts(phase)
  const partOptions: SelectorOption<PartSelection>[] =
    parts.length === 0
      ? []
      : [
          { value: ALL_PARTS, label: t('battle.detail.allParts') },
          ...parts.map((each) => ({
            value: each.id,
            label: <BattlePartLabel config={config} id={id} phaseId={phaseId} part={each} />,
          })),
        ]
  const egoList = phase.egoList ?? []

  const selector = (
    <BattleSelectorRow
      phaseCount={phases.length}
      phaseIndex={selection.phaseIndex}
      onPhaseChange={(phaseIndex) => {
        setSelection({ phaseIndex, part: ALL_PARTS })
      }}
      partOptions={partOptions}
      part={part === undefined ? ALL_PARTS : part.id}
      onPartChange={(next) => {
        setSelection((previous) => ({ ...previous, part: next }))
      }}
      levels={engagementLevels(engagements, fallbackLevel)}
      level={level}
      onLevelChange={setLevel}
    />
  )

  return (
    <EntityDetailLayout
      header={
        <BattleDetailHeader family={family} id={id} config={config} phase={phase} badge={badge} />
      }
      metadata={
        <BattleInfoPane
          config={config}
          id={id}
          phase={phase}
          part={part}
          level={level}
          tier={tier}
          engagements={engagements}
        />
      }
      selector={selector}
    >
      <DetailSection title={t('section.skills')}>
        <div className="border rounded divide-y">
          {visibleSkills(phase, part).map(({ id: skillId, entry }) => (
            <BattleSkillCard
              key={skillId}
              source={source}
              imageOwnerId={imageOwnerId}
              skillId={skillId}
              entry={entry}
              tier={tier}
              palette={palette}
            />
          ))}
        </div>
      </DetailSection>
      <DetailSection title={t('section.passives')}>
        <BattlePassivesPane
          source={source}
          phase={phase}
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
          <BattleSanityPane sanity={phase.sanity} />
        </DetailSection>
      )}
    </EntityDetailLayout>
  )
}
