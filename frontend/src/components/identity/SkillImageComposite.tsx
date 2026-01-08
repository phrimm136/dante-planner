import { getSkillImagePath, getSkillImagePathFromIconID } from '@/lib/assetPaths'
import { useState } from 'react'
import { SkillImageComposite as CommonSkillImageComposite } from '@/components/common/SkillImageComposite'
import type { IdentitySkillDataEntry } from '@/types/IdentityTypes'
import type { SkillAttributeType } from '@/lib/constants'

interface SkillImageCompositeProps {
  identityId: string
  skillId: number
  skillTier: number
  skillData: IdentitySkillDataEntry
}

/**
 * Identity-specific wrapper for SkillImageComposite
 * Uses iconID when present for cross-skill icon references
 */
export function SkillImageComposite({
  identityId,
  skillId,
  skillTier,
  skillData,
}: SkillImageCompositeProps) {
  const [showMissing, setShowMissing] = useState(false)

  const attributeType = (skillData.attributeType ?? 'NEUTRAL') as SkillAttributeType
  const atkType = skillData.atkType
  const basePower = skillData.defaultValue ?? 0
  const coinPower = skillData.scale ?? 0

  // Use iconID if present (cross-skill reference), otherwise use skillId
  const skillImagePath = skillData.iconID
    ? getSkillImagePathFromIconID(skillData.iconID)
    : getSkillImagePath(identityId, String(skillId))

  return (
    <CommonSkillImageComposite
      skillImagePath={skillImagePath}
      attributeType={attributeType}
      skillTier={skillTier}
      atkType={atkType}
      basePower={basePower}
      coinPower={coinPower}
      onImageError={() => setShowMissing(true)}
      showMissingPlaceholder={showMissing}
    />
  )
}
