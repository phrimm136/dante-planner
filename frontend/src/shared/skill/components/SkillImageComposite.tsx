import {
  getSkillFramePath,
  getSkillFrameBGPath,
  getAttackTypeIconPath,
  getAttackTypeFramePath,
  getAttackTypeFrameBGPath,
} from '@/shared/assets'
import { type SkillAttributeType } from '@/shared/gameData'
import { getDisplayFontForNumeric } from '@/lib/utils'
import { SKILL_FRAME_GLOW_COLORS } from '@/lib/constants'

interface SkillImageCompositeProps {
  skillImagePath: string
  attributeType: SkillAttributeType
  skillTier: number
  atkType?: string | undefined
  basePower: number
  coinPower: number
  onImageError?: () => void
  showMissingPlaceholder?: boolean
}

export function SkillImageComposite({
  skillImagePath,
  attributeType,
  skillTier,
  atkType,
  basePower,
  coinPower,
  onImageError,
  showMissingPlaceholder = false,
}: SkillImageCompositeProps) {
  const frameBGPath = getSkillFrameBGPath(attributeType, skillTier)
  const framePath = getSkillFramePath(attributeType, skillTier)
  const glowColor = SKILL_FRAME_GLOW_COLORS[attributeType ?? 'NEUTRAL']

  return (
    <div className="relative w-32 h-32 shrink-0 ml-2">
      <div className="absolute top-1/64 left-1/2 transform -translate-x-9/16 w-8 h-8 flex items-center justify-center">
        <div
          className="absolute w-12 h-9"
          style={{
            backgroundColor: glowColor,
            clipPath: 'polygon(5% 20%, 85% 0%, 100% 100%, 10% 100%)',
          }}
        />
      </div>
      <img
        src={frameBGPath}
        alt=""
        className="absolute inset-0 w-full h-full object-contain pointer-events-none"
      />

      <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
        <div className="relative w-16 h-16">
          {!showMissingPlaceholder ? (
            <img
              src={skillImagePath}
              alt=""
              className="w-full h-full object-cover"
              style={{
                clipPath:
                  'polygon(30% 0%, 70% 0%, 100% 30%, 100% 70%, 70% 100%, 30% 100%, 0% 70%, 0% 30%)',
              }}
              onError={onImageError}
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center text-xs text-muted-foreground">
              Missing
            </div>
          )}
        </div>
      </div>

      <img
        src={framePath}
        alt=""
        className="absolute inset-0 w-full h-full object-contain pointer-events-none"
      />

      {atkType && atkType !== 'NONE' && (
        <div className="absolute bottom-0 left-1/2 -translate-x-1/2 -translate-y-3/8 w-8 h-8 pointer-events-none">
          <img
            src={getAttackTypeFrameBGPath(attributeType)}
            alt=""
            className="absolute inset-0 w-lg h-lg object-contain"
          />

          <img
            src={getAttackTypeFramePath(attributeType)}
            alt=""
            className="absolute inset-0 w-full h-full object-contain"
          />

          <div className="absolute inset-0 flex items-center justify-center">
            <img
              src={getAttackTypeIconPath(atkType)}
              alt={atkType}
              className="w-4 h-4 object-contain"
            />
          </div>
        </div>
      )}

      <div className="absolute left-0 top-1/2 -translate-x-5/16 -translate-y-6/8 w-16 h-16 flex justify-end pr-[24px]">
        <div
          className="text-[48px] [-webkit-text-stroke:0.01px_black] text-power"
          style={{ fontFamily: getDisplayFontForNumeric() }}
        >
          {basePower}
        </div>
      </div>

      <div className="absolute top-0 left-1/2 -translate-x-9/16 -translate-y-1/8 w-14 h-8 flex items-center justify-center">
        <div
          className="relative text-[28px] stroke-black font-bold [-webkit-text-stroke:0.01px_black]"
          style={{ fontFamily: getDisplayFontForNumeric() }}
        >
          {coinPower > 0 ? `+${coinPower}` : coinPower}
        </div>
      </div>
    </div>
  )
}
