import type { CSSProperties } from 'react'

import { getAttributeColors } from '@/shared/gameData'
import {
  getIdentityProfileImagePath,
  getIdentityImageFallbackPath,
  getAttackTypeIconPath,
} from '@/shared/assets'
import { OFFENSIVE_SKILL_SLOTS, DEFAULT_SKILL_EA } from '@/shared/gameData'
import { aspectOf } from '@/shared/cardLayout'
import { SINNER_SKILL_GEOMETRY } from '../../lib/cardLayout'
import type { SkillEAState, UptieTier, SkillInfo } from '../../types/DeckTypes'
import { SINNER_SKILL_CARD, cqw } from '../../lib/cardLayout'
import { cn } from '@/lib/utils'

interface SinnerSkillCardProps {
  identityId: string
  uptie: UptieTier
  rank: number
  skillInfos: [SkillInfo, SkillInfo, SkillInfo]
  skillEA: SkillEAState
  currentEA?: SkillEAState | undefined
  onClick: () => void
  readOnly?: boolean
}

const ROOT_STYLE: CSSProperties = {
  aspectRatio: aspectOf(SINNER_SKILL_GEOMETRY.size),
  padding: cqw(SINNER_SKILL_CARD.padding),
  gap: cqw(SINNER_SKILL_CARD.rowGap),
}

const BADGE_STYLE: CSSProperties = {
  width: cqw(SINNER_SKILL_CARD.badge),
  height: cqw(SINNER_SKILL_CARD.badge),
  fontSize: cqw(SINNER_SKILL_CARD.badgeFontSize),
}

const BADGE_OFFSET = `-${cqw(SINNER_SKILL_CARD.badgeOffset)}`

const ICON_SIZE = cqw(SINNER_SKILL_CARD.atkIcon)

/**
 * SinnerSkillCard - Clickable card showing identity and skill info with EA
 *
 * Layout (vertical):
 * - Identity image with uptie frame (top)
 * - Skill row with attack type icons on affinity-colored backgrounds + EA badges (bottom)
 */
export function SinnerSkillCard({
  identityId,
  uptie,
  skillInfos,
  skillEA,
  currentEA,
  onClick,
  readOnly = false,
}: SinnerSkillCardProps) {
  const isDefaultEA = OFFENSIVE_SKILL_SLOTS.every(
    (slot) => skillEA[slot] === DEFAULT_SKILL_EA[slot],
  )
  const matchesCurrentEA =
    currentEA !== undefined &&
    OFFENSIVE_SKILL_SLOTS.every((slot) => skillEA[slot] === currentEA[slot])
  const isDimmed = isDefaultEA || matchesCurrentEA

  return (
    <button
      onClick={readOnly ? undefined : onClick}
      disabled={readOnly}
      className={cn(
        'w-full flex flex-col items-center rounded-lg',
        'bg-card',
        'transition-all',
        !readOnly && 'selectable',
        isDimmed && 'opacity-60',
      )}
      style={ROOT_STYLE}
    >
      {/* Identity image */}
      <div
        className="relative"
        style={{
          width: cqw(SINNER_SKILL_CARD.portrait),
          aspectRatio: 1,
        }}
      >
        <div className="absolute inset-0 flex items-center justify-center overflow-hidden">
          <img
            src={getIdentityProfileImagePath(identityId, uptie)}
            onError={(e) => {
              const target = e.currentTarget
              if (!target.dataset.fallback) {
                target.dataset.fallback = 'true'
                target.src = getIdentityImageFallbackPath(identityId)
              }
            }}
            alt={identityId}
          />
        </div>
      </div>

      {/* Skill Info Row - atkType icon on affinity background with EA badge */}
      <div className="flex" style={{ gap: cqw(SINNER_SKILL_CARD.skillGap) }}>
        {OFFENSIVE_SKILL_SLOTS.map((slot) => {
          const affinity = skillInfos[slot].attributeType
          const atkType = skillInfos[slot].atkType
          const bgColor = affinity ? getAttributeColors(affinity).primary : undefined
          const ea = skillEA[slot]

          return (
            <div key={slot} className="relative">
              <div
                className="rounded-sm flex items-center justify-center"
                style={{
                  width: cqw(SINNER_SKILL_CARD.skillBox),
                  height: cqw(SINNER_SKILL_CARD.skillBox),
                  backgroundColor: bgColor || 'var(--muted)',
                }}
                title={`Skill ${slot + 1}: ${atkType || '?'} (${affinity || '?'}) - EA: ${ea}`}
              >
                {atkType ? (
                  <img
                    src={getAttackTypeIconPath(atkType)}
                    alt={atkType}
                    className="object-contain"
                    style={{ width: ICON_SIZE, height: ICON_SIZE }}
                  />
                ) : (
                  <div style={{ width: ICON_SIZE, height: ICON_SIZE }} />
                )}
              </div>
              {/* Planned EA badge, upper-right */}
              <div
                className="absolute rounded-full bg-primary flex items-center justify-center"
                style={{ ...BADGE_STYLE, top: BADGE_OFFSET, right: BADGE_OFFSET }}
              >
                <span className="font-bold text-primary-foreground">{ea}</span>
              </div>
              {/* Current EA badge, lower-right — tracker mode only */}
              {currentEA !== undefined && (
                <div
                  className="absolute rounded-full bg-accent flex items-center justify-center"
                  style={{ ...BADGE_STYLE, bottom: BADGE_OFFSET, right: BADGE_OFFSET }}
                >
                  <span className="font-bold text-accent-foreground">{currentEA[slot]}</span>
                </div>
              )}
            </div>
          )
        })}
      </div>
    </button>
  )
}
