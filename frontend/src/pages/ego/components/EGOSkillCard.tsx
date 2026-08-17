import { useEGODetailI18n } from '../hooks/useEGODetailData'
import {
  SkillCard,
  SkillDescription,
  getMergedSkillDesc,
  mergeSkillDataUpToLevel,
} from '@/shared/skill'
import { StyledSkillName } from '@/shared/gameText'
import { getEGOSkillImagePath } from '@/shared/assets'
import type { EGOSkillEntry, Threadspin } from '../types/EGOTypes'
import type { SkillId } from '@/shared/gameData'

interface EGOSkillCardWithGranularI18nProps {
  egoId: string
  skillType: 'awaken' | 'erosion'
  skillEntry: EGOSkillEntry
  threadspin: Threadspin
}

/**
 * EGO adapter over the shared SkillCard: merges threadspin data and resolves the
 * awaken/erosion skill image path, then delegates. Fixed frame tier 3; supplies
 * the sanity (MP) cost stat.
 */
export function EGOSkillCardWithGranularI18n({
  egoId,
  skillType,
  skillEntry,
  threadspin,
}: EGOSkillCardWithGranularI18nProps) {
  const mergedData = mergeSkillDataUpToLevel(skillEntry.skillData, threadspin)
  const coinString = mergedData.coinString ?? ''

  return (
    <SkillCard
      skillData={mergedData}
      coinString={coinString}
      skillImagePath={getEGOSkillImagePath(egoId, skillType)}
      skillTier={3}
      nameSlot={
        <EGOSkillName
          egoId={egoId}
          skillId={skillEntry.id}
          attributeType={mergedData.attributeType}
        />
      }
      descriptionSlot={
        <EGOSkillDescription egoId={egoId} skillId={skillEntry.id} level={threadspin} />
      }
      sanityCost={mergedData.mpUsage ?? 0}
    />
  )
}

/**
 * Suspending name slot — resolves the skill name from EGO detail i18n.
 */
function EGOSkillName({
  egoId,
  skillId,
  attributeType,
}: {
  egoId: string
  skillId: SkillId
  attributeType?: string | undefined
}) {
  const i18n = useEGODetailI18n(egoId)
  return (
    <StyledSkillName
      name={i18n.skills[skillId]?.name ?? ''}
      {...(attributeType !== undefined && { attributeType })}
    />
  )
}

/**
 * Suspending description slot — merges EGO skill descriptions up to the threadspin.
 */
function EGOSkillDescription({
  egoId,
  skillId,
  level,
}: {
  egoId: string
  skillId: SkillId
  level: Threadspin
}) {
  const i18n = useEGODetailI18n(egoId)
  const skillI18n = i18n.skills[skillId]
  return (
    <SkillDescription
      descData={getMergedSkillDesc(skillI18n?.descs ?? [], level)}
      {...(skillI18n?.flavor !== undefined && { flavor: skillI18n.flavor })}
    />
  )
}
