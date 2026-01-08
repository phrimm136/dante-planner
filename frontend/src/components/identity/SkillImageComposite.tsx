import { getSkillImagePath } from '@/lib/assetPaths'
import { useState } from 'react'
import { SkillImageComposite as CommonSkillImageComposite } from '@/components/common/SkillImageComposite'
import type { IdentitySkillDataEntry, Uptie } from '@/types/IdentityTypes'
import type { SkillAttributeType } from '@/lib/constants'

interface SkillImageCompositeProps {
  identityId: string
  skillSlot: number
  variantIndex: number
  skillData: IdentitySkillDataEntry
  uptie: Uptie
}

/**
 * Identity-specific wrapper for SkillImageComposite
 * Handles uptie-based image fallback logic
 */
export function SkillImageComposite({
  identityId,
  skillSlot,
  variantIndex,
  skillData,
  uptie
}: SkillImageCompositeProps) {
  // Track which image variant to use: 'uptie4' -> 'uptie3' -> 'missing'
  const [imageVariant, setImageVariant] = useState<'uptie4' | 'uptie3' | 'missing'>(
    uptie === 4 ? 'uptie4' : 'uptie3'
  )

  const attributeType = (skillData.attributeType ?? 'NEUTRAL') as SkillAttributeType
  const atkType = skillData.atkType
  const basePower = skillData.defaultValue ?? 0
  const coinPower = skillData.scale ?? 0
  // Use skillTier from data for frame selection, fallback to skillSlot for backwards compat
  const skillTier = skillData.skillTier ?? skillSlot

  // Determine which image path to use based on current variant
  const useUptie4 = imageVariant === 'uptie4'
  const skillImagePath = getSkillImagePath(identityId, skillSlot, variantIndex, useUptie4)

  // Handle image load error with two-stage fallback
  const handleImageError = () => {
    if (imageVariant === 'uptie4') {
      // First fallback: try uptie3 version
      setImageVariant('uptie3')
    } else if (imageVariant === 'uptie3') {
      // Second fallback: show missing placeholder
      setImageVariant('missing')
    }
  }

  return (
    <CommonSkillImageComposite
      skillImagePath={skillImagePath}
      attributeType={attributeType}
      skillTier={skillTier}
      atkType={atkType}
      basePower={basePower}
      coinPower={coinPower}
      onImageError={handleImageError}
      showMissingPlaceholder={imageVariant === 'missing'}
    />
  )
}
