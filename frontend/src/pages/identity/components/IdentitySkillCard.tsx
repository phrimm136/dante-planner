import { useIdentityDetailI18n } from '../hooks/useIdentityDetailData'
import { SkillCard, mergeSkillDataUpToLevel } from '@/shared/skill'
import { getSkillImagePath, getSkillImagePathFromIconID } from '@/shared/assets'
import type { IdentitySkillEntry, Uptie } from '../types/IdentityTypes'

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
  for (let i = 0; i < skillData.length; i++) {
    if (Object.keys(skillData[i]).length > 0) {
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
      entityId={identityId}
      skillId={textId}
      level={uptie}
      skillData={mergedData}
      coinString={coinString}
      skillImagePath={skillImagePath}
      skillTier={skillEntry.skillTier ?? skillSlot}
      useDetailI18n={useIdentityDetailI18n}
      isDefenseSkill={isDefenseSkill}
      isLocked={isLocked}
    />
  )
}
