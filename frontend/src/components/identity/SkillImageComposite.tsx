import { SIN_COLORS, type SinType } from '@/lib/globalConstants'
import {
  getSkillImagePath,
  getSinFramePath,
  getSinFrameBGPath,
  getAttackTypeIconPath,
  getAttackTypeFramePath,
  getAttackTypeFrameBGPath,
} from '@/lib/identityUtils'
import { useState, useMemo } from 'react'
import { getCachedFrame } from '@/lib/frameCache'
import type { SkillData } from '@/types/IdentityTypes'

interface SkillImageCompositeProps {
  identityId: string
  skillSlot: number
  variantIndex: number
  skillData: SkillData
  isUptie4?: boolean
}

/**
 * SkillImageComposite - Complete skill image with layered composition
 *
 * Layer structure (bottom to top):
 * 1. Sin frame background (colored)
 * 2. Skill image
 * 3. Sin frame (colored)
 * 4. Attack type composite (for offensive skills):
 *    - Attack type frame background (colored)
 *    - Attack type icon
 *    - Attack type frame (colored)
 * 5. Power text overlays (basePower left, coinPower top)
 *
 * Colors applied via CSS multiply blend mode
 */
export function SkillImageComposite({
  identityId,
  skillSlot,
  variantIndex,
  skillData,
  isUptie4 = false,
}: SkillImageCompositeProps) {
  const [imageError, setImageError] = useState(false)
  const [useUptie4, setUseUptie4] = useState(isUptie4)

  const { basePower, coinPower, sin, atkType } = skillData

  // Get image paths
  const skillImagePath = getSkillImagePath(identityId, skillSlot, variantIndex, useUptie4)
  const sinFrameBGPath = getSinFrameBGPath(sin, skillSlot)
  const sinFramePath = getSinFramePath(sin, skillSlot)

  // Get sin colors for multiplication
  const sinColors = sin ? SIN_COLORS[sin as SinType] : SIN_COLORS.defense

  // Get pre-rendered frames from cache (synchronous, no loading needed)
  const coloredFrameBG = useMemo(
    () => getCachedFrame(sinFrameBGPath, sinColors.bg),
    [sinFrameBGPath, sinColors.bg]
  )
  const coloredFrame = useMemo(
    () => getCachedFrame(sinFramePath, sinColors.fg),
    [sinFramePath, sinColors.fg]
  )

  // Get pre-rendered attack type frames from cache
  const coloredAttackBG = useMemo(
    () => (atkType ? getCachedFrame(getAttackTypeFrameBGPath(), sinColors.bg) : null),
    [atkType, sinColors.bg]
  )
  const coloredAttackFrame = useMemo(
    () => (atkType ? getCachedFrame(getAttackTypeFramePath(), sinColors.fg) : null),
    [atkType, sinColors.fg]
  )

  // Handle image load error - fallback to non-uptie4 version
  const handleImageError = () => {
    if (useUptie4 && !imageError) {
      setUseUptie4(false)
      setImageError(false)
    } else {
      setImageError(true)
    }
  }

  return (
    <div className="relative w-32 h-32 shrink-0">
      {/* Layer 1: Colored sin frame background */}
      {coloredFrameBG && (
        <img
          src={coloredFrameBG}
          alt=""
          className="absolute inset-0 w-full h-full object-contain pointer-events-none"
        />
      )}

      {/* Layer 2: Skill image */}
      <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
        <div className="relative w-16 h-16">
          {!imageError ? (
            <img
              src={skillImagePath}
              alt="Skill"
              className="w-full h-full object-cover"
              style={{ clipPath: 'polygon(30% 0%, 70% 0%, 100% 30%, 100% 70%, 70% 100%, 30% 100%, 0% 70%, 0% 30%)' }}
              onError={handleImageError}
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center text-xs text-muted-foreground">
              Missing
            </div>
          )}
        </div>
      </div>

      {/* Layer 3: Colored sin frame */}
      {coloredFrame && (
        <img
          src={coloredFrame}
          alt=""
          className="absolute inset-0 w-full h-full object-contain pointer-events-none"
        />
      )}

      {/* Layer 4: Attack type composite (skills with attack type only) */}
      {atkType && (
        <div className="absolute bottom-0 left-1/2 -translate-x-1/2 translate-y-8/32 w-28 h-28 pointer-events-none">
          {/* Colored attack type frame background */}
          {coloredAttackBG && (
            <img
              src={coloredAttackBG}
              alt=""
              className="absolute inset-0 w-full h-full object-contain"
            />
          )}

          {/* Colored attack type frame */}
          {coloredAttackFrame && (
            <img
              src={coloredAttackFrame}
              alt=""
              className="absolute inset-0 w-full h-full object-contain"
            />
          )}

          {/* Attack type icon - centered with reserved size */}
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="relative w-4 h-4">
              <img
                src={getAttackTypeIconPath(atkType)}
                alt={atkType}
                className="w-full h-full object-contain"
              />
            </div>
          </div>
        </div>
      )}

      {/* Layer 5: Base power (left side) */}
      <div className="absolute left-0 top-1/2 -translate-y-1/2 w-8 h-8 flex items-center justify-center">
        <div className="text-lg font-bold drop-shadow-[0_2px_2px_rgba(0,0,0,0.8)]">
          {basePower}
        </div>
      </div>

      {/* Layer 5: Coin power (top side) */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-8 h-8 flex items-center justify-center">
        <div className="text-lg font-bold drop-shadow-[0_2px_2px_rgba(0,0,0,0.8)]">
          +{coinPower}
        </div>
      </div>
    </div>
  )
}
