import { useIdentityDetailI18n } from '../hooks/useIdentityDetailData'
import { IdentitySkillCard, SkillDescription, getMergedSkillDesc } from '@/shared/skill'
import { StyledSkillName } from '@/shared/gameText'
import type { IdentitySkillEntry, Uptie } from '../types/IdentityTypes'
import type { SkillId } from '@/shared/gameData'

interface IdentitySkillCardWithGranularI18nProps {
  identityId: string
  skillSlot: number
  skillEntry: IdentitySkillEntry
  uptie: Uptie
  level: number
  isLocked?: boolean
}

/**
 * Smallest 1-based uptie at which the skill is first defined.
 * Empty `{}` slots before that level mean "not yet introduced" (not "inherit"),
 * so the renderer should lock and display this skill at its first defined level.
 * Falls back to 1 when no slot has data — keeps the card visible rather than blank.
 */
export function getFirstDefinedUptie(skillData: IdentitySkillEntry['skillData']): Uptie {
  for (const [i, levelData] of skillData.entries()) {
    if (Object.keys(levelData).length > 0) {
      return (i + 1) as Uptie
    }
  }
  return 1
}

export function IdentitySkillCardWithGranularI18n({
  identityId,
  skillSlot,
  skillEntry,
  uptie,
  level,
  isLocked = false,
}: IdentitySkillCardWithGranularI18nProps) {
  const textId = skillEntry.textID ?? skillEntry.id

  return (
    <IdentitySkillCard
      identityId={identityId}
      skillSlot={skillSlot}
      skillEntry={skillEntry}
      uptie={uptie}
      level={level}
      renderName={(attributeType) => (
        <IdentitySkillName identityId={identityId} skillId={textId} attributeType={attributeType} />
      )}
      descriptionSlot={
        <IdentitySkillDescription identityId={identityId} skillId={textId} level={uptie} />
      }
      isLocked={isLocked}
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
    <StyledSkillName
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
