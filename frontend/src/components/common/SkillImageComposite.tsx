import {
  getSkillFramePath,
  getSkillFrameBGPath,
  getAttackTypeIconPath,
  getAttackTypeFramePath,
  getAttackTypeFrameBGPath,
} from '@/lib/assetPaths'
import type { SkillAttributeType } from '@/lib/constants'

interface SkillImageCompositeProps {
  skillImagePath: string
  attributeType: SkillAttributeType
  skillSlot: number
  atkType?: string
  basePower: number
  coinPower: number
  onImageError?: () => void
  showMissingPlaceholder?: boolean
}

/**
 * SkillImageComposite - Reusable skill image with layered composition
 *
 * Layer structure (bottom to top):
 * 1. Skill frame background (colored by attribute)
 * 2. Skill image (octagonal clip-path)
 * 3. Skill frame (colored by attribute)
 * 4. Attack type composite (for offensive skills):
 *    - Attack type frame background (colored)
 *    - Attack type icon
 *    - Attack type frame (colored)
 * 5. Power text overlays (basePower left, coinPower top)
 */
export function SkillImageComposite({
  skillImagePath,
  attributeType,
  skillSlot,
  atkType,
  basePower,
  coinPower,
  onImageError,
  showMissingPlaceholder = false,
}: SkillImageCompositeProps) {
  const frameBGPath = getSkillFrameBGPath(attributeType, skillSlot)
  const framePath = getSkillFramePath(attributeType, skillSlot)

  return (
    <div className="relative w-32 h-32 shrink-0">
      {/* Layer 1: Skill frame background */}
      <img
        src={frameBGPath}
        alt=""
        className="absolute inset-0 w-full h-full object-contain pointer-events-none"
      />

      {/* Layer 2: Skill image */}
      <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
        <div className="relative w-16 h-16">
          {!showMissingPlaceholder ? (
            <img
              src={skillImagePath}
              alt="Skill"
              className="w-full h-full object-cover"
              style={{ clipPath: 'polygon(30% 0%, 70% 0%, 100% 30%, 100% 70%, 70% 100%, 30% 100%, 0% 70%, 0% 30%)' }}
              onError={onImageError}
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center text-xs text-muted-foreground">
              Missing
            </div>
          )}
        </div>
      </div>

      {/* Layer 3: Skill frame */}
      <img
        src={framePath}
        alt=""
        className="absolute inset-0 w-full h-full object-contain pointer-events-none"
      />

      {/* Layer 4: Attack type composite (skills with attack type only) */}
      {atkType && atkType !== "NONE" && (
        <div className="absolute bottom-0 left-1/2 -translate-x-1/2 -translate-y-3/8 w-8 h-8 pointer-events-none">
          {/* Attack type frame background */}
          <img
            src={getAttackTypeFrameBGPath(attributeType)}
            alt=""
            className="absolute inset-0 w-lg h-lg object-contain"
          />

          {/* Attack type frame */}
          <img
            src={getAttackTypeFramePath(attributeType)}
            alt=""
            className="absolute inset-0 w-full h-full object-contain"
          />

          {/* Attack type icon - centered with reserved size */}
          <div className="absolute inset-0 flex items-center justify-center">
            <img
              src={getAttackTypeIconPath(atkType)}
              alt={atkType}
              className="w-4 h-4 -translate-x-1/16 object-contain"
            />
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
