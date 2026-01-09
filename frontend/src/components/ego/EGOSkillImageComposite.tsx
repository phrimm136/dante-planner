import { getEGOSkillImagePath } from '@/lib/assetPaths'
import { SkillImageComposite as CommonSkillImageComposite } from '@/components/common/SkillImageComposite'
import type { EGOSkillDataEntry } from '@/types/EGOTypes'
import type { SkillAttributeType } from '@/lib/constants'

interface EGOSkillImageCompositeProps {
  egoId: string
  skillType: 'awaken' | 'erosion'
  skillData: EGOSkillDataEntry
}

/**
 * EGO-specific wrapper for SkillImageComposite
 * Uses awaken/erosion skill images
 */
export function EGOSkillImageComposite({
  egoId,
  skillType,
  skillData,
}: EGOSkillImageCompositeProps) {
  const attributeType = (skillData.attributeType ?? 'NEUTRAL') as SkillAttributeType
  const atkType = skillData.atkType
  const basePower = skillData.defaultValue ?? 0
  const coinPower = skillData.scale ?? 0
  const skillTier = 3

  const skillImagePath = getEGOSkillImagePath(egoId, skillType)

  return (
    <CommonSkillImageComposite
      skillImagePath={skillImagePath}
      attributeType={attributeType}
      skillTier={skillTier}
      atkType={atkType}
      basePower={basePower}
      coinPower={coinPower}
    />
  )
}
