import type { ReactNode } from 'react'

import { getEGOSkillImagePath } from '@/shared/assets'
import { EGO_SKILL_TYPES } from '@/shared/gameData'
import type { SkillId } from '@/shared/gameData'
import { toSkillCardData } from '../lib/skillLevel'
import type { ImportancePalette, SkillEntry, SkillLevel } from '@/shared/gameData'
import { SkillCard } from './SkillCard'

interface EGOSkillCardProps {
  egoId: string
  skillId: SkillId
  entry: SkillEntry
  level: SkillLevel
  unitLevel: number | undefined
  renderName: (attributeType: string | undefined) => ReactNode
  descriptionSlot: ReactNode
  importanceLevel: number
  palette: ImportancePalette
}

export function EGOSkillCard({
  egoId,
  skillId,
  entry,
  level,
  unitLevel,
  renderName,
  descriptionSlot,
  importanceLevel,
  palette,
}: EGOSkillCardProps) {
  const cardData = toSkillCardData(entry, level)
  const imageType = entry.skillType === EGO_SKILL_TYPES.EROSION ? 'erosion' : 'awaken'

  return (
    <SkillCard
      skillId={skillId}
      skillData={cardData}
      coins={level.coins}
      skillImagePath={getEGOSkillImagePath(egoId, imageType)}
      skillTier={entry.skillTier}
      nameSlot={renderName(cardData.attributeType)}
      descriptionSlot={descriptionSlot}
      unitLevel={unitLevel}
      importanceLevel={importanceLevel}
      palette={palette}
      sanityCost={cardData.mpUsage}
    />
  )
}
