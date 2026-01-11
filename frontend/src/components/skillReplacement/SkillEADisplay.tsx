import { SkillImageSimple } from './SkillImageSimple'
import { getSkillImagePath } from '@/lib/assetPaths'
import type { SkillAttributeType, OffensiveSkillSlot } from '@/lib/constants'

interface SkillEADisplayProps {
  identityId: string
  skillSlot: OffensiveSkillSlot
  attributeType: SkillAttributeType
  atkType?: string
  ea: number
  currentEA?: number
}

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
    <div className="relative">
      <SkillImageSimple
        skillImagePath={skillImagePath}
        attributeType={attributeType}
        skillTier={skillSlot + 1}
        atkType={atkType}
      />

      {/* Planned EA Badge (오른쪽 위) */}
      <div className="absolute -top-1 -right-1 w-7 h-7 rounded-full bg-primary flex items-center justify-center">
        <span className="text-sm font-bold text-primary-foreground">{ea}</span>
      </div>

      {/* Current EA Badge (오른쪽 아래) */}
      {currentEA !== undefined && (
        <div className="absolute -bottom-1 -right-1 w-7 h-7 rounded-full bg-accent flex items-center justify-center">
          <span className="text-sm font-bold text-accent-foreground">{currentEA}</span>
        </div>
      )}
    </div>
  )
}
