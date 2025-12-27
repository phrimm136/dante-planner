import { MAX_LEVEL } from '@/lib/constants'
import { CoinDisplay } from './CoinDisplay'
import type { IdentitySkillDataEntry } from '@/types/IdentityTypes'

interface SkillInfoPanelProps {
  skillName: string
  skillData: IdentitySkillDataEntry
  coinString: string
  isDefenseSkill: boolean
}

/**
 * SkillInfoPanel - Displays skill name and specifications
 *
 * Layout (vertical):
 * 1. Coin display
 * 2. Skill name
 * 3. Attack/Defense level with icon
 */
export function SkillInfoPanel({
  skillName,
  skillData,
  coinString,
  isDefenseSkill,
}: SkillInfoPanelProps) {
  const skillLevelCorrection = skillData.skillLevelCorrection ?? 0

  // Calculate total level (max + skill level correction), ensure at least 1
  const totalLevel = Math.max(1, MAX_LEVEL + skillLevelCorrection)

  return (
    <div className="flex flex-col gap-2">
      {/* Coin display */}
      <div>
        <CoinDisplay coinEA={coinString} />
      </div>

      {/* Skill name */}
      <div className="flex items-baseline gap-2">
        <span className="font-semibold">{skillName}</span>
      </div>

      {/* Level display */}
      <div className="flex items-center gap-3 text-sm">
        <div className="flex items-center gap-1">
          <img
            src={
              isDefenseSkill
                ? '/images/UI/identity/defense.webp'
                : '/images/UI/identity/attack.webp'
            }
            alt={isDefenseSkill ? 'Defense' : 'Attack'}
            className="w-4 h-4"
          />
          <span className="underline">{totalLevel}</span>
        </div>
      </div>
    </div>
  )
}
