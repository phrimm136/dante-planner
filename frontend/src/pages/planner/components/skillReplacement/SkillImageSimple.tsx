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
  /** Skill tier (1-3) determines frame appearance */
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

/**
 * SkillImageSimple - Skill image with layers 1-4 only (no power text)
 *
 * Layer structure (bottom to top):
 * 1. Skill frame background (colored by attribute)
 * 2. Skill image (octagonal clip-path)
 * 3. Skill frame (colored by attribute)
 * 4. Attack type composite (for offensive skills)
 *
 * Used in skill replacement pane where power values are not shown.
 */
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
      {/* Layer 1: Skill frame background */}
      <img
        src={frameBGPath}
        alt=""
        className="absolute inset-0 w-full h-full object-contain pointer-events-none"
      />

      {/* Layer 2: Skill image */}
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

      {/* Layer 3: Skill frame */}
      <img
        src={framePath}
        alt=""
        className="absolute inset-0 w-full h-full object-contain pointer-events-none"
      />

      {/* Layer 4: Attack type composite (skills with attack type only) */}
      {atkType && (
        <div
          className="absolute bottom-0 left-1/2 -translate-x-1/2 -translate-y-3/8 pointer-events-none"
          style={ATK_COMPOSITE_STYLE}
        >
          {/* Attack type frame background */}
          <img
            src={getAttackTypeFrameBGPath(attributeType)}
            alt=""
            className="absolute inset-0 w-full h-full object-contain"
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
              className="-translate-x-1/16 object-contain"
              style={ATK_ICON_STYLE}
            />
          </div>
        </div>
      )}
    </div>
  )
}
