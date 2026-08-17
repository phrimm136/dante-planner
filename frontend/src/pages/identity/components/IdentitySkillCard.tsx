import { useIdentityDetailI18n } from '../hooks/useIdentityDetailData'
import {
  SkillCard,
  SkillDescription,
  getMergedSkillDesc,
  mergeSkillDataUpToLevel,
} from '@/shared/skill'
import { StyledSkillName } from '@/shared/gameText'
import { getSkillImagePath, getSkillImagePathFromIconID } from '@/shared/assets'
import type { IdentitySkillEntry, Uptie } from '../types/IdentityTypes'
import type { SkillId } from '@/shared/gameData'

interface IdentitySkillCardWithGranularI18nProps {
  identityId: string
  skillSlot: number
  skillEntry: IdentitySkillEntry
  uptie: Uptie
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

/**
 * Identity adapter over the shared SkillCard: merges uptie data and resolves the
 * identity skill image path (iconID cross-reference or id), then delegates.
 */
export function IdentitySkillCardWithGranularI18n({
  identityId,
  skillSlot,
  skillEntry,
  uptie,
  isLocked = false,
}: IdentitySkillCardWithGranularI18nProps) {
  const mergedData = mergeSkillDataUpToLevel(skillEntry.skillData, uptie)
  const isDefenseSkill = mergedData.atkType === 'NONE' || !mergedData.atkType
  const coinString = mergedData.coinString ?? ''
  const textId = skillEntry.textID ?? skillEntry.id

  const skillImagePath = mergedData.iconID
    ? getSkillImagePathFromIconID(mergedData.iconID)
    : getSkillImagePath(identityId, String(skillEntry.id))

  return (
    <SkillCard
      skillData={mergedData}
      coinString={coinString}
      skillImagePath={skillImagePath}
      skillTier={skillEntry.skillTier ?? skillSlot}
      nameSlot={
        <IdentitySkillName
          identityId={identityId}
          skillId={textId}
          attributeType={mergedData.attributeType}
        />
      }
      descriptionSlot={
        <IdentitySkillDescription identityId={identityId} skillId={textId} level={uptie} />
      }
      isDefenseSkill={isDefenseSkill}
      isLocked={isLocked}
    />
  )
}

/**
 * Suspending name slot — resolves the skill name from identity detail i18n.
 */
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

/**
 * Suspending description slot — merges identity skill descriptions up to the uptie.
 */
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
