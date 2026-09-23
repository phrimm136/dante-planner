import type { ReactNode } from 'react'

import { getSkillImagePath, getSkillImagePathFromIconID } from '@/shared/assets'
import type { SkillId } from '@/shared/gameData'
import { toSkillCardData } from '../lib/skillLevel'
import type { SkillEntry, SkillLevel } from '@/shared/gameData'
import { SkillCard } from './SkillCard'

interface IdentitySkillCardProps {
  identityId: string
  skillId: SkillId
  entry: SkillEntry
  level: SkillLevel
  unitLevel: number
  renderName: (attributeType: string | undefined) => ReactNode
  descriptionSlot: ReactNode
  isLocked?: boolean
}

export function IdentitySkillCard({
  identityId,
  skillId,
  entry,
  level,
  unitLevel,
  renderName,
  descriptionSlot,
  isLocked = false,
}: IdentitySkillCardProps) {
  const cardData = toSkillCardData(entry, level)
  const isDefenseSkill = cardData.atkType === 'NONE' || !cardData.atkType

  const skillImagePath = level.iconID
    ? getSkillImagePathFromIconID(level.iconID)
    : getSkillImagePath(identityId, skillId)

  return (
    <SkillCard
      skillId={skillId}
      skillData={cardData}
      coinString={level.coinString}
      skillImagePath={skillImagePath}
      skillTier={entry.skillTier}
      level={unitLevel}
      nameSlot={renderName(cardData.attributeType)}
      descriptionSlot={descriptionSlot}
      isDefenseSkill={isDefenseSkill}
      isLocked={isLocked}
    />
  )
}
