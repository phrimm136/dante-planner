import { useEGODetailI18n } from '../hooks/useEGODetailData'
import { SkillCard, mergeSkillDataUpToLevel } from '@/shared/skill'
import { getEGOSkillImagePath } from '@/shared/assets'
import type { EGOSkillEntry, Threadspin } from '../types/EGOTypes'

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
      entityId={egoId}
      skillId={skillEntry.id}
      level={threadspin}
      skillData={mergedData}
      coinString={coinString}
      skillImagePath={getEGOSkillImagePath(egoId, skillType)}
      skillTier={3}
      useDetailI18n={useEGODetailI18n}
      sanityCost={mergedData.mpUsage ?? 0}
    />
  )
}
