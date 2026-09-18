import type { CSSProperties } from 'react'

import {
  getSkillFramePath,
  getSkillFrameBGPath,
  getAttackTypeIconPath,
  getAttackTypeFramePath,
  getAttackTypeFrameBGPath,
} from '@/shared/assets'
import { aspectOf } from '@/shared/cardLayout'
import { SKILL_IMAGE_GEOMETRY } from '../../lib/cardLayout'
import type { SkillAttributeType } from '@/shared/gameData'
import { SKILL_IMAGE_CARD, pct } from '../../lib/cardLayout'

interface SkillImageSimpleProps {
  skillImagePath: string
  attributeType: SkillAttributeType
  skillTier: number
  atkType?: string | undefined
  onImageError?: () => void
  showMissingPlaceholder?: boolean
}

const ROOT_STYLE: CSSProperties = {
  containerType: 'inline-size',
  aspectRatio: aspectOf(SKILL_IMAGE_GEOMETRY.size),
}

const ART_STYLE: CSSProperties = {
  width: pct(SKILL_IMAGE_CARD.art),
  height: pct(SKILL_IMAGE_CARD.art),
}

const ATK_COMPOSITE_STYLE: CSSProperties = {
  width: pct(SKILL_IMAGE_CARD.atkComposite),
  height: pct(SKILL_IMAGE_CARD.atkComposite),
}

const ATK_ICON_STYLE: CSSProperties = {
  width: pct(SKILL_IMAGE_CARD.atkIcon),
  height: pct(SKILL_IMAGE_CARD.atkIcon),
}

const SKILL_CLIP = 'polygon(30% 0%, 70% 0%, 100% 30%, 100% 70%, 70% 100%, 30% 100%, 0% 70%, 0% 30%)'

export function SkillImageSimple({
  skillImagePath,
  attributeType,
  skillTier,
  atkType,
  onImageError,
  showMissingPlaceholder = false,
}: SkillImageSimpleProps) {
  const frameBGPath = getSkillFrameBGPath(attributeType, skillTier)
  const framePath = getSkillFramePath(attributeType, skillTier)

  return (
    <div className="relative w-full shrink-0" style={ROOT_STYLE}>
      <img
        src={frameBGPath}
        alt=""
        className="absolute inset-0 w-full h-full object-contain pointer-events-none"
      />

      <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
        <div className="relative" style={ART_STYLE}>
          {!showMissingPlaceholder ? (
            <img
              src={skillImagePath}
              alt=""
              className="w-full h-full object-cover"
              style={{ clipPath: SKILL_CLIP }}
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

      {atkType && (
        <div
          className="absolute bottom-0 left-1/2 -translate-x-1/2 -translate-y-3/8 pointer-events-none"
          style={ATK_COMPOSITE_STYLE}
        >
          <img
            src={getAttackTypeFrameBGPath(attributeType)}
            alt=""
            className="absolute inset-0 w-full h-full object-contain"
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
              className="-translate-x-1/16 object-contain"
              style={ATK_ICON_STYLE}
            />
          </div>
        </div>
      )}
    </div>
  )
}
