import { Suspense } from 'react'

import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { FormattedDescription, StyledSkillName } from '@/shared/gameText'
import { EGOPassiveCard, EGOPassivesPane } from '@/shared/passive'
import {
  EGOSkillCard,
  SkillDescription,
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

function EgoName({ egoId }: { egoId: string }) {
  const i18n = useBattleEgoDetailI18n(egoId)
  return <h3 className="text-lg font-semibold">{i18n.name}</h3>
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
    <StyledSkillName
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

function EgoPassiveName({ egoId, passiveId }: { egoId: string; passiveId: string }) {
  const i18n = useBattleEgoDetailI18n(egoId)
  return (
    <StyledSkillName name={i18n.passives[passiveId]?.name ?? passiveId} attributeType="NEUTRAL" />
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
  palette,
}: {
  egoId: string
  skillId: SkillId
  entry: SkillEntry
  egoLevel: number
  palette: ImportancePalette
}) {
  const { level } = selectSkillLevel(entry.levels, egoLevel)
  return (
    <EGOSkillCard
      egoId={egoId}
      skillId={skillId}
      entry={entry}
      level={level}
      renderName={(attributeType) => (
        <EgoSkillName egoId={egoId} skillId={skillId} attributeType={attributeType} />
      )}
      descriptionSlot={<EgoSkillDescription egoId={egoId} skillId={skillId} level={egoLevel} />}
      importanceLevel={entry.importanceLevel}
      palette={palette}
    />
  )
}

function EgoBlock({ ego, palette }: { ego: EgoEntry; palette: ImportancePalette }) {
  const egoId = String(ego.egoID)
  const spec = useBattleEgoDetailSpec(egoId)

  return (
    <div data-ego-id={egoId} className="space-y-2">
      <Suspense fallback={<TextSkeleton size="lg" width="sm" />}>
        <EgoName egoId={egoId} />
      </Suspense>
      <div className="border rounded divide-y">
        {orderEgoSkills(spec.skills).map(({ id: skillId, entry }) => (
          <EgoSkillCard
            key={skillId}
            egoId={egoId}
            skillId={skillId}
            entry={entry}
            egoLevel={ego.egoLevel}
            palette={palette}
          />
        ))}
      </div>
      <EGOPassivesPane
        passives={spec.passives}
        threadspinLevel={ego.egoLevel as Threadspin}
        renderPassive={(passiveId, isLocked) => (
          <div data-passive-id={passiveId} data-locked={isLocked}>
            <EGOPassiveCard
              nameSlot={<EgoPassiveName egoId={egoId} passiveId={passiveId} />}
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
  palette,
}: {
  egoList: readonly EgoEntry[]
  palette: ImportancePalette
}) {
  return (
    <div className="space-y-6">
      {egoList.map((ego) => (
        <Suspense key={ego.egoID} fallback={<TextSkeleton lines={3} width="full" />}>
          <EgoBlock ego={ego} palette={palette} />
        </Suspense>
      ))}
    </div>
  )
}
