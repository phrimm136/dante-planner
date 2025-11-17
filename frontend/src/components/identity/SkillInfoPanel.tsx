import { BASE_LEVEL } from '@/lib/globalConstants'
import { CoinDisplay } from './CoinDisplay'
import type { SkillData } from '@/types/IdentityTypes'

interface SkillInfoPanelProps {
  skillName: string
  skillData: SkillData
  skillEA: number
  isDefenseSkill: boolean
}

/**
 * SkillInfoPanel - Displays skill name and specifications
 *
 * Layout (vertical):
 * 1. Coin EA display
 * 2. Skill name and EA count
 * 3. Attack/Defense level with icon
 * 4. Attack weight
 */
export function SkillInfoPanel({
  skillName,
  skillData,
  skillEA,
  isDefenseSkill,
}: SkillInfoPanelProps) {
  const { coinEA, LV, atkWeight } = skillData

  // Calculate total level (base + skill LV)
  const totalLevel = BASE_LEVEL + LV

  return (
    <div className="flex flex-col gap-2">
      {/* Coin EA */}
      <div>
        <CoinDisplay coinEA={coinEA} />
      </div>

      {/* Skill name and EA count */}
      <div className="flex items-baseline gap-2">
        <span className="font-semibold">{skillName}</span>
        <span className="text-sm text-muted-foreground">x{skillEA}</span>
      </div>

      {/* Level and weight display - horizontal */}
      <div className="flex items-center gap-3 text-sm">
        {/* Level */}
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

        {/* Attack weight (offensive skills only) */}
        {!isDefenseSkill && (
          <div className="text-muted-foreground">Weight: {atkWeight}</div>
        )}
      </div>
    </div>
  )
}
