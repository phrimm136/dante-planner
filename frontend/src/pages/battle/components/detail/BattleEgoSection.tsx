import { Suspense } from 'react'

import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { FormattedDescription, NamePlate, type NamePlateImportance } from '@/shared/gameText'
import { EGOPassiveCard, EGOPassivesPane } from '@/shared/passive'
import { getEGORankIconPath } from '@/shared/assets'
import {
  EGOSkillCard,
  SkillDescription,
  SkillList,
  getMergedSkillDesc,
  orderEgoSkills,
  selectSkillLevel,
} from '@/shared/skill'
import type { ImportancePalette, SkillEntry, SkillId, Threadspin } from '@/shared/gameData'
import { useBattleEgoDetailI18n, useBattleEgoDetailSpec } from '../../hooks/useBattleEgoDetailData'

interface EgoEntry {
  egoID: number
  egoLevel: number
}

function EgoSkillName({
  egoId,
  skillId,
  attributeType,
}: {
  egoId: string
  skillId: SkillId
  attributeType?: string | undefined
}) {
  const i18n = useBattleEgoDetailI18n(egoId)
  return (
    <NamePlate
      kind="skill"
      name={i18n.skills[skillId]?.name ?? skillId}
      {...(attributeType !== undefined && { attributeType })}
    />
  )
}

function EgoSkillDescription({
  egoId,
  skillId,
  level,
}: {
  egoId: string
  skillId: SkillId
  level: number
}) {
  const i18n = useBattleEgoDetailI18n(egoId)
  return (
    <SkillDescription descData={getMergedSkillDesc(i18n.skills[skillId]?.descs ?? [], level)} />
  )
}

function EgoPassiveName({
  egoId,
  passiveId,
  importance,
}: {
  egoId: string
  passiveId: string
  importance: NamePlateImportance
}) {
  const i18n = useBattleEgoDetailI18n(egoId)
  return (
    <NamePlate
      kind="passive"
      name={i18n.passives[passiveId]?.name ?? passiveId}
      attributeType="NEUTRAL"
      importance={importance}
    />
  )
}

function EgoPassiveDesc({ egoId, passiveId }: { egoId: string; passiveId: string }) {
  const i18n = useBattleEgoDetailI18n(egoId)
  return <FormattedDescription text={i18n.passives[passiveId]?.desc ?? ''} />
}

function EgoSkillCard({
  egoId,
  skillId,
  entry,
  egoLevel,
  unitLevel,
  palette,
}: {
  egoId: string
  skillId: SkillId
  entry: SkillEntry
  egoLevel: number
  unitLevel: number | undefined
  palette: ImportancePalette
}) {
  const { level } = selectSkillLevel(entry.levels, egoLevel)
  return (
    <EGOSkillCard
      egoId={egoId}
      skillId={skillId}
      entry={entry}
      level={level}
      unitLevel={unitLevel}
      renderName={(attributeType) => (
        <EgoSkillName egoId={egoId} skillId={skillId} attributeType={attributeType} />
      )}
      descriptionSlot={<EgoSkillDescription egoId={egoId} skillId={skillId} level={egoLevel} />}
      importanceLevel={entry.importanceLevel}
      palette={palette}
    />
  )
}

function EgoBlock({
  ego,
  unitLevel,
  palette,
}: {
  ego: EgoEntry
  unitLevel: number | undefined
  palette: ImportancePalette
}) {
  const egoId = String(ego.egoID)
  const spec = useBattleEgoDetailSpec(egoId)

  return (
    <div data-ego-id={egoId} className="space-y-2">
      <img
        src={getEGORankIconPath(spec.egoType)}
        alt={`${spec.egoType} rank`}
        className="h-6 object-contain"
      />
      <SkillList
        skills={orderEgoSkills(spec.skills)}
        renderSkill={({ id: skillId, entry }) => (
          <EgoSkillCard
            egoId={egoId}
            skillId={skillId}
            entry={entry}
            egoLevel={ego.egoLevel}
            unitLevel={unitLevel}
            palette={palette}
          />
        )}
      />
      <EGOPassivesPane
        passives={spec.passives}
        threadspinLevel={ego.egoLevel as Threadspin}
        renderPassive={(passiveId, isLocked) => (
          <div data-passive-id={passiveId} data-locked={isLocked}>
            <EGOPassiveCard
              renderName={(importance) => (
                <EgoPassiveName egoId={egoId} passiveId={passiveId} importance={importance} />
              )}
              descSlot={<EgoPassiveDesc egoId={egoId} passiveId={passiveId} />}
              flavorSlot={null}
              isLocked={isLocked}
              importanceLevel={0}
              palette={palette}
            />
          </div>
        )}
      />
    </div>
  )
}

export function BattleEgoSection({
  egoList,
  unitLevel,
  palette,
}: {
  egoList: readonly EgoEntry[]
  unitLevel: number | undefined
  palette: ImportancePalette
}) {
  return (
    <div className="space-y-6">
      {egoList.map((ego) => (
        <Suspense key={ego.egoID} fallback={<TextSkeleton lines={3} width="full" />}>
          <EgoBlock ego={ego} unitLevel={unitLevel} palette={palette} />
        </Suspense>
      ))}
    </div>
  )
}
