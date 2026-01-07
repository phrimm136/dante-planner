import { getIdentityProfileImagePath, getIdentityImageFallbackPath, getAttackTypeIconPath } from '@/lib/assetPaths'
import { OFFENSIVE_SKILL_SLOTS } from '@/lib/constants'
import type { SkillEAState, UptieTier, SkillInfo } from '@/types/DeckTypes'
import { cn } from '@/lib/utils'
import colorCode from '@static/data/colorCode.json'

interface SinnerSkillCardProps {
  identityId: string
  uptie: UptieTier
  rank: number
  skillInfos: [SkillInfo, SkillInfo, SkillInfo]
  skillEA: SkillEAState
  onClick: () => void
}

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
  onClick,
}: SinnerSkillCardProps) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'flex flex-col items-center gap-1 p-2 rounded-lg',
        'border-2 border-border  bg-card',
        'transition-all cursor-pointer selectable'
      )}
    >
      {/* Identity image */}
      <div className="relative w-24 h-24">
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
      <div className="flex gap-1">
        {OFFENSIVE_SKILL_SLOTS.map((slot) => {
          const affinity = skillInfos[slot].attributeType
          const atkType = skillInfos[slot].atkType
          const bgColor = affinity ? (colorCode as Record<string, string>)[affinity] : undefined
          const ea = skillEA[slot]

          return (
            <div key={slot} className="relative">
              <div
                className="w-7 h-7 rounded-sm flex items-center justify-center"
                style={{ backgroundColor: bgColor || 'var(--muted)' }}
                title={`Skill ${slot + 1}: ${atkType || '?'} (${affinity || '?'}) - EA: ${ea}`}
              >
                {atkType ? (
                  <img
                    src={getAttackTypeIconPath(atkType)}
                    alt={atkType}
                    className="w-5 h-5 object-contain"
                  />
                ) : (
                  <div className="w-5 h-5" />
                )}
              </div>
              {/* EA Badge */}
              <div className="absolute -top-1 -right-1 w-4 h-4 rounded-full bg-primary flex items-center justify-center">
                <span className="text-[10px] font-bold text-primary-foreground">{ea}</span>
              </div>
            </div>
          )
        })}
      </div>
    </button>
  )
}
