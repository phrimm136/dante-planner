import {
  getSkillFramePath,
  getSkillFrameBGPath,
  getAttackTypeIconPath,
  getAttackTypeFramePath,
  getAttackTypeFrameBGPath,
} from '@/shared/assets'
import { type SkillAttributeType } from '@/shared/gameData'
import { PANEL_COIN_NUMBER_SHADOW } from '@/shared/gameText'
import { getDisplayFontForNumeric } from '@/lib/utils'
import { SKILL_FRAME_GLOW_COLORS, UNIT_PANEL_COLORS } from '@/lib/constants'
import { skillUpperLayout } from '../lib/skillLayout'

interface SkillImageCompositeProps {
  skillImagePath: string | undefined
  attributeType: SkillAttributeType
  skillTier: number
  atkType?: string | undefined
  basePower: number
  coinPower: number
  onImageError?: () => void
  showMissingPlaceholder?: boolean
}

const LAYOUT = skillUpperLayout()

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
    <>
      <div
        data-slot="coin-scale-bg"
        className="absolute"
        style={{
          ...LAYOUT.coinScaleBg,
          backgroundColor: glowColor,
          clipPath: 'polygon(5% 20%, 85% 0%, 100% 100%, 10% 100%)',
        }}
      />
      <img
        src={frameBGPath}
        alt=""
        data-slot="skill-frame-bg"
        className="absolute object-contain pointer-events-none"
        style={LAYOUT.icon}
      />

      {skillImagePath !== undefined && (
        <div
          className="absolute flex items-center justify-center pointer-events-none"
          style={LAYOUT.icon}
        >
          <div className="relative w-1/2 h-1/2">
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
      )}

      <img
        src={framePath}
        alt=""
        data-slot="skill-frame"
        className="absolute object-contain pointer-events-none"
        style={LAYOUT.icon}
      />

      {atkType && atkType !== 'NONE' && (
        <div
          data-slot="attack-icon"
          className="absolute flex items-center justify-center pointer-events-none"
          style={LAYOUT.attackIcon}
        >
          <img
            src={getAttackTypeFrameBGPath(attributeType)}
            alt=""
            data-slot="attack-backing"
            className="absolute inset-0 w-full h-full object-contain"
          />
          <img
            src={getAttackTypeFramePath(attributeType)}
            alt=""
            className="absolute inset-0 w-full h-full object-contain"
          />
          <img
            src={getAttackTypeIconPath(atkType)}
            alt={atkType}
            className="relative w-1/2 h-1/2 object-contain"
          />
        </div>
      )}

      <div
        data-slot="base-power"
        className="absolute flex justify-end leading-none"
        style={{
          ...LAYOUT.basePower,
          color: UNIT_PANEL_COLORS.BASE_POWER,
          textShadow: PANEL_COIN_NUMBER_SHADOW,
          fontFamily: getDisplayFontForNumeric(),
        }}
      >
        {basePower}
      </div>

      <div
        data-slot="coin-scale"
        className="absolute leading-none"
        style={{
          ...LAYOUT.coinScale,
          color: UNIT_PANEL_COLORS.COIN_SCALE,
          textShadow: PANEL_COIN_NUMBER_SHADOW,
          fontFamily: getDisplayFontForNumeric(),
        }}
      >
        {coinPower > 0 ? `+${coinPower}` : coinPower}
      </div>
    </>
  )
}
