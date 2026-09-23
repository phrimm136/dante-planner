import type { ReactNode } from 'react'

import { getEGOSkillImagePath } from '@/shared/assets'
import { EGO_SKILL_TYPES } from '@/shared/gameData'
import type { SkillId } from '@/shared/gameData'
import { toSkillCardData } from '../lib/skillLevel'
import type { SkillEntry, SkillLevel } from '@/shared/gameData'
import { SkillCard } from './SkillCard'

interface EGOSkillCardProps {
  egoId: string
  skillId: SkillId
  entry: SkillEntry
  level: SkillLevel
  unitLevel: number
  renderName: (attributeType: string | undefined) => ReactNode
  descriptionSlot: ReactNode
}

export function EGOSkillCard({
  egoId,
  skillId,
  entry,
  level,
  unitLevel,
  renderName,
  descriptionSlot,
}: EGOSkillCardProps) {
  const cardData = toSkillCardData(entry, level)
  const imageType = entry.skillType === EGO_SKILL_TYPES.EROSION ? 'erosion' : 'awaken'

  return (
    <SkillCard
      skillId={skillId}
      skillData={cardData}
      coinString={level.coinString}
      skillImagePath={getEGOSkillImagePath(egoId, imageType)}
      skillTier={entry.skillTier}
      level={unitLevel}
      nameSlot={renderName(cardData.attributeType)}
      descriptionSlot={descriptionSlot}
      sanityCost={cardData.mpUsage}
    />
  )
}
