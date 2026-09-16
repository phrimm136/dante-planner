import type { CSSProperties } from 'react'

import { SkillImageSimple } from './SkillImageSimple'
import { getSkillImagePath } from '@/shared/assets'
import type { SkillAttributeType, OffensiveSkillSlot } from '@/shared/gameData'
import { SKILL_IMAGE_CARD, cqw } from '../../lib/cardLayout'

interface SkillEADisplayProps {
  identityId: string
  skillSlot: OffensiveSkillSlot
  attributeType: SkillAttributeType
  atkType?: string | undefined
  ea: number
  currentEA?: number | undefined
}

const BADGE_STYLE: CSSProperties = {
  width: cqw(SKILL_IMAGE_CARD.badge),
  height: cqw(SKILL_IMAGE_CARD.badge),
  fontSize: cqw(SKILL_IMAGE_CARD.badgeFontSize),
}

const BADGE_OFFSET = `-${cqw(SKILL_IMAGE_CARD.badgeOffset)}`

/**
 * SkillEADisplay - Skill image with EA (Exchange Allowance) badge
 *
 * Shows the skill image (layers 1-4) with an EA count badge overlay.
 * Used in skill replacement section and exchange modal.
 */
export function SkillEADisplay({
  identityId,
  skillSlot,
  attributeType,
  atkType,
  ea,
  currentEA,
}: SkillEADisplayProps) {
  // Construct skill ID
  const skillId = identityId + (skillSlot + 1).toString().padStart(2, '0')
  const skillImagePath = getSkillImagePath(identityId, skillId)

  return (
    <div className="relative w-full">
      <SkillImageSimple
        skillImagePath={skillImagePath}
        attributeType={attributeType}
        skillTier={skillSlot + 1}
        atkType={atkType}
      />

      {/* Planned EA badge, upper-right */}
      <div
        className="absolute rounded-full bg-primary flex items-center justify-center"
        style={{ ...BADGE_STYLE, top: BADGE_OFFSET, right: BADGE_OFFSET }}
      >
        <span className="font-bold text-primary-foreground">{ea}</span>
      </div>

      {/* Current EA badge, lower-right */}
      {currentEA !== undefined && (
        <div
          className="absolute rounded-full bg-accent flex items-center justify-center"
          style={{ ...BADGE_STYLE, bottom: BADGE_OFFSET, right: BADGE_OFFSET }}
        >
          <span className="font-bold text-accent-foreground">{currentEA}</span>
        </div>
      )}
    </div>
  )
}
