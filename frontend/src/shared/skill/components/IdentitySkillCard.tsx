import type { ReactNode } from 'react'

import { getSkillImagePath, getSkillImagePathFromIconID } from '@/shared/assets'
import type { SkillId, Uptie } from '@/shared/gameData'
import { mergeSkillDataUpToLevel } from '../lib/mergeSkillData'
import { SkillCard } from './SkillCard'

interface IdentitySkillLevelData {
  attributeType?: string | undefined
  atkType?: string | undefined
  targetNum?: number | undefined
  mpUsage?: number | undefined
  skillLevelCorrection?: number | undefined
  defaultValue?: number | undefined
  scale?: number | undefined
  iconID?: string | undefined
  coinString?: string | undefined
}

interface IdentitySkillCardEntry {
  id: SkillId
  skillTier?: number | undefined
  skillData: IdentitySkillLevelData[]
}

interface IdentitySkillCardProps {
  identityId: string
  skillSlot: number
  skillEntry: IdentitySkillCardEntry
  uptie: Uptie
  level: number
  renderName: (attributeType: string | undefined) => ReactNode
  descriptionSlot: ReactNode
  isLocked?: boolean
}

export function IdentitySkillCard({
  identityId,
  skillSlot,
  skillEntry,
  uptie,
  level,
  renderName,
  descriptionSlot,
  isLocked = false,
}: IdentitySkillCardProps) {
  const mergedData = mergeSkillDataUpToLevel(skillEntry.skillData, uptie)
  const isDefenseSkill = mergedData.atkType === 'NONE' || !mergedData.atkType
  const coinString = mergedData.coinString ?? ''

  const skillImagePath = mergedData.iconID
    ? getSkillImagePathFromIconID(mergedData.iconID)
    : getSkillImagePath(identityId, String(skillEntry.id))

  return (
    <SkillCard
      skillData={mergedData}
      coinString={coinString}
      skillImagePath={skillImagePath}
      level={level}
      skillTier={skillEntry.skillTier ?? skillSlot}
      nameSlot={renderName(mergedData.attributeType)}
      descriptionSlot={descriptionSlot}
      isDefenseSkill={isDefenseSkill}
      isLocked={isLocked}
    />
  )
}
