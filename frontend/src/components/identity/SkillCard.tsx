import { SkillImageComposite } from './SkillImageComposite'
import { SkillInfoPanelWithSuspense } from './SkillInfoPanel'
import { SkillDescriptionWithSuspense } from './SkillDescription'
import { SkillCardLayout } from '@/components/common/SkillCardLayout'
import type { IdentitySkillEntry, IdentitySkillDataEntry, Uptie } from '@/types/IdentityTypes'

interface SkillCardWithGranularI18nProps {
  identityId: string
  skillSlot: number
  skillEntry: IdentitySkillEntry
  uptie: Uptie
}

/**
 * Get merged skill data for a specific uptie level.
 * Earlier uptie levels provide base values, later levels override.
 */
function getMergedSkillData(
  skillData: IdentitySkillEntry['skillData'],
  uptie: Uptie
): IdentitySkillDataEntry {
  const merged: IdentitySkillDataEntry = {}
  for (let i = 0; i < uptie; i++) {
    Object.assign(merged, skillData[i])
  }
  return merged
}

/**
 * SkillCard with granular i18n Suspense.
 * Structure (image, stats) stays visible, only name/description suspends.
 */
export function SkillCardWithGranularI18n({
  identityId,
  skillSlot,
  skillEntry,
  uptie,
}: SkillCardWithGranularI18nProps) {
  const mergedData = getMergedSkillData(skillEntry.skillData, uptie)
  const isDefenseSkill = mergedData.atkType === 'NONE' || !mergedData.atkType
  const coinString = mergedData.coinString ?? ''
  const textId = skillEntry.textID ?? skillEntry.id

  return (
    <SkillCardLayout
      imageComposite={
        <SkillImageComposite
          identityId={identityId}
          skillId={skillEntry.id}
          skillTier={skillEntry.skillTier ?? skillSlot}
          skillData={mergedData}
        />
      }
      infoPanel={
        <SkillInfoPanelWithSuspense
          identityId={identityId}
          skillId={textId}
          skillData={mergedData}
          coinString={coinString}
          isDefenseSkill={isDefenseSkill}
        />
      }
      description={
        <SkillDescriptionWithSuspense
          identityId={identityId}
          skillId={textId}
          uptie={uptie}
        />
      }
    />
  )
}
