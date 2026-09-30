import type { ReactNode } from 'react'

import { getSkillImagePath, getSkillImagePathFromIconID } from '@/shared/assets'
import type { SkillId } from '@/shared/gameData'
import { toSkillCardData } from '../lib/skillLevel'
import type { ImportancePalette, SkillEntry, SkillLevel } from '@/shared/gameData'
import { SkillCard } from './SkillCard'

interface IdentitySkillCardProps {
  identityId: string | undefined
  skillId: SkillId
  entry: SkillEntry
  level: SkillLevel
  unitLevel: number | undefined
  renderName: (attributeType: string | undefined) => ReactNode
  descriptionSlot: ReactNode
  importanceLevel: number
  palette: ImportancePalette
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
  importanceLevel,
  palette,
  isLocked = false,
}: IdentitySkillCardProps) {
  const cardData = toSkillCardData(entry, level)
  const isDefenseSkill = cardData.atkType === 'NONE' || !cardData.atkType

  const skillImagePath = level.iconID
    ? getSkillImagePathFromIconID(level.iconID)
    : identityId === undefined
      ? undefined
      : getSkillImagePath(identityId, skillId)

  return (
    <SkillCard
      skillId={skillId}
      skillData={cardData}
      coins={level.coins}
      skillImagePath={skillImagePath}
      skillTier={entry.skillTier}
      nameSlot={renderName(cardData.attributeType)}
      descriptionSlot={descriptionSlot}
      unitLevel={unitLevel}
      importanceLevel={importanceLevel}
      palette={palette}
      isDefenseSkill={isDefenseSkill}
      isLocked={isLocked}
    />
  )
}
