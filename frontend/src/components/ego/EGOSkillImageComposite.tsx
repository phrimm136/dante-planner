import {
  getEGOSkillImagePath,
  getSinFramePath,
  getSinFrameBGPath,
  getAttackTypeIconPath,
  getAttackTypeFramePath,
  getAttackTypeFrameBGPath,
} from '@/lib/identityUtils'
import type { EGOSkillData } from '@/types/EGOTypes'

interface EGOSkillImageCompositeProps {
  egoId: string
  skillType: 'awakening' | 'corrosion'
  skillData: EGOSkillData
  sin: string
  threadspin: '3' | '4'
}

/**
 * EGOSkillImageComposite - Complete EGO skill image with layered composition
 *
 * Layer structure (bottom to top):
 * 1. Sin frame background (colored)
 * 2. Skill image (awaken_profile or erosion_profile)
 * 3. Sin frame (colored)
 * 4. Attack type composite:
 *    - Attack type frame background (colored)
 *    - Attack type icon
 *    - Attack type frame (colored)
 * 5. Power text overlays (basePower left, coinPower top)
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
  const sinFrameBGPath = getSinFrameBGPath(sin, skillSlot)
  const sinFramePath = getSinFramePath(sin, skillSlot)

  return (
    <div className="relative w-32 h-32 shrink-0">
      {/* Layer 1: Sin frame background */}
      <img
        src={sinFrameBGPath}
        alt=""
        className="absolute inset-0 w-full h-full object-contain pointer-events-none"
      />

      {/* Layer 2: Skill image */}
      <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
        <div className="relative w-16 h-16">
          <img
            src={skillImagePath}
            alt=""
            className="w-full h-full object-contain"
            style={{
              clipPath:
                'polygon(30% 0%, 70% 0%, 100% 30%, 100% 70%, 70% 100%, 30% 100%, 0% 70%, 0% 30%)',
            }}
          />
        </div>
      </div>

      {/* Layer 3: Sin frame */}
      <img
        src={sinFramePath}
        alt=""
        className="absolute inset-0 w-full h-full object-contain pointer-events-none"
      />

      {/* Layer 4: Attack type composite (bottom-left corner) */}
      {atkType && (
        <div className="absolute bottom-0 left-0 w-10 h-10 pointer-events-none">
          {/* Attack type frame background */}
          <img
            src={getAttackTypeFrameBGPath(sin)}
            alt=""
            className="absolute inset-0 w-full h-full object-contain"
          />

          {/* Attack type icon */}
          <img
            src={getAttackTypeIconPath(atkType)}
            alt={atkType}
            className="absolute inset-0 w-full h-full object-contain p-1.5"
          />

          {/* Attack type frame */}
          <img
            src={getAttackTypeFramePath(sin)}
            alt=""
            className="absolute inset-0 w-full h-full object-contain"
          />
        </div>
      )}

      {/* Layer 5: Power overlays */}
      {/* Base power (left side) */}
      <div className="absolute left-0 top-1/2 -translate-y-1/2 text-xs font-bold text-white pointer-events-none px-1">
        {basePower}
      </div>

      {/* Coin power (top center) */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 text-xs font-bold text-white pointer-events-none py-1">
        {coinPower > 0 ? `+${coinPower}` : coinPower}
      </div>
    </div>
  )
}
