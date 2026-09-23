import type { ReactNode } from 'react'

import { getEGOSkillImagePath } from '@/shared/assets'
import type { Threadspin } from '@/shared/gameData'
import { mergeSkillDataUpToLevel } from '../lib/mergeSkillData'
import { SkillCard } from './SkillCard'

interface EGOSkillLevelData {
  attributeType?: string | undefined
  atkType?: string | undefined
  targetNum?: number | undefined
  mpUsage?: number | undefined
  skillLevelCorrection?: number | undefined
  defaultValue?: number | undefined
  scale?: number | undefined
  coinString?: string | undefined
}

interface EGOSkillCardEntry {
  skillData: EGOSkillLevelData[]
}

interface EGOSkillCardProps {
  egoId: string
  skillType: 'awaken' | 'erosion'
  skillEntry: EGOSkillCardEntry
  threadspin: Threadspin
  level: number
  renderName: (attributeType: string | undefined) => ReactNode
  descriptionSlot: ReactNode
}

export function EGOSkillCard({
  egoId,
  skillType,
  skillEntry,
  threadspin,
  level,
  renderName,
  descriptionSlot,
}: EGOSkillCardProps) {
  const mergedData = mergeSkillDataUpToLevel(skillEntry.skillData, threadspin)
  const coinString = mergedData.coinString ?? ''

  return (
    <SkillCard
      skillData={mergedData}
      coinString={coinString}
      skillImagePath={getEGOSkillImagePath(egoId, skillType)}
      level={level}
      skillTier={3}
      nameSlot={renderName(mergedData.attributeType)}
      descriptionSlot={descriptionSlot}
      sanityCost={mergedData.mpUsage ?? 0}
    />
  )
}
