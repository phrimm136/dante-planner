import { useIdentityDetailI18n } from '../hooks/useIdentityDetailData'
import {
  IdentitySkillCard,
  SkillDescription,
  getMergedSkillDesc,
  selectSkillLevel,
} from '@/shared/skill'
import { NamePlate } from '@/shared/gameText'
import type { Uptie } from '../types/IdentityTypes'
import type { SkillEntry, SkillId } from '@/shared/gameData'

interface IdentitySkillCardWithGranularI18nProps {
  identityId: string
  skillId: SkillId
  entry: SkillEntry
  uptie: Uptie
  unitLevel: number
}

export function IdentitySkillCardWithGranularI18n({
  identityId,
  skillId,
  entry,
  uptie,
  unitLevel,
}: IdentitySkillCardWithGranularI18nProps) {
  const { level, isLocked } = selectSkillLevel(entry.levels, uptie)
  const descriptionLevel = isLocked ? (level.level as Uptie) : uptie

  return (
    <IdentitySkillCard
      identityId={identityId}
      skillId={skillId}
      entry={entry}
      level={level}
      unitLevel={unitLevel}
      renderName={(attributeType) => (
        <IdentitySkillName
          identityId={identityId}
          skillId={skillId}
          attributeType={attributeType}
        />
      )}
      descriptionSlot={
        <IdentitySkillDescription
          identityId={identityId}
          skillId={skillId}
          level={descriptionLevel}
        />
      }
      isLocked={isLocked}
      importanceLevel={entry.importanceLevel}
      palette="player"
    />
  )
}

function IdentitySkillName({
  identityId,
  skillId,
  attributeType,
}: {
  identityId: string
  skillId: SkillId
  attributeType?: string | undefined
}) {
  const i18n = useIdentityDetailI18n(identityId)
  return (
    <NamePlate
      kind="skill"
      name={i18n.skills[skillId]?.name ?? ''}
      {...(attributeType !== undefined && { attributeType })}
    />
  )
}

function IdentitySkillDescription({
  identityId,
  skillId,
  level,
}: {
  identityId: string
  skillId: SkillId
  level: Uptie
}) {
  const i18n = useIdentityDetailI18n(identityId)
  const skillI18n = i18n.skills[skillId]
  return (
    <SkillDescription
      descData={getMergedSkillDesc(skillI18n?.descs ?? [], level)}
      {...(skillI18n?.flavor !== undefined && { flavor: skillI18n.flavor })}
    />
  )
}
