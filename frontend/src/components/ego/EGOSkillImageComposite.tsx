import { getEGOSkillImagePath } from '@/lib/assetPaths'
import { SkillImageComposite as CommonSkillImageComposite } from '@/components/common/SkillImageComposite'
import type { EGOSkillData } from '@/types/EGOTypes'

interface EGOSkillImageCompositeProps {
  egoId: string
  skillType: 'awakening' | 'corrosion'
  skillData: EGOSkillData
  sin: string
  threadspin: '3' | '4'
}

/**
 * EGO-specific wrapper for SkillImageComposite
 * Uses awakening/corrosion skill images
 */
export function EGOSkillImageComposite({
  egoId,
  skillType,
  skillData,
  sin,
  threadspin
}: EGOSkillImageCompositeProps) {
  const { atkType, threadspins } = skillData

  // Get current threadspin data (first element of array)
  const threadspinData = threadspins[threadspin][0]
  const basePower = threadspinData.basePower
  const coinPower = threadspinData.coinPower

  // Use skill type (awakening/corrosion) to determine skill slot for sin frame
  const skillSlot = skillType === 'awakening' ? 1 : 2

  const skillImagePath = getEGOSkillImagePath(egoId, skillType)

  return (
    <CommonSkillImageComposite
      skillImagePath={skillImagePath}
      sin={sin}
      skillSlot={skillSlot}
      atkType={atkType}
      basePower={basePower}
      coinPower={coinPower}
    />
  )
}
