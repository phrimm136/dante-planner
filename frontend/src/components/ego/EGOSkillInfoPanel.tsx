import { MAX_LEVEL } from '@/lib/constants'
import { CoinDisplay } from '@/components/identity/CoinDisplay'
import type { EGOSkillDataEntry } from '@/types/EGOTypes'

interface EGOSkillInfoPanelProps {
  skillName: string
  skillData: EGOSkillDataEntry
  coinString: string
}

/**
 * EGOSkillInfoPanel - Displays EGO skill name and specifications
 *
 * Layout (vertical):
 * 1. Coin display
 * 2. Skill name
 * 3. Attack level with icon
 * 4. Target count and sanity cost
 */
export function EGOSkillInfoPanel({
  skillName,
  skillData,
  coinString,
}: EGOSkillInfoPanelProps) {
  const skillLevelCorrection = skillData.skillLevelCorrection ?? 0
  const targetNum = skillData.targetNum ?? 1
  const sanityCost = skillData.mpUsage ?? 0

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

      {/* Level and stats display - horizontal */}
      <div className="flex items-center gap-3 text-sm">
        {/* Level */}
        <div className="flex items-center gap-1">
          <img
            src="/images/UI/identity/attack.webp"
            alt="Attack"
            className="w-4 h-4"
          />
          <span className="underline">{totalLevel}</span>
        </div>

        {/* Target count */}
        <div className="text-muted-foreground">
          Target: {targetNum}
        </div>

        {/* Sanity cost */}
        <div className="text-muted-foreground">
          Sanity: {sanityCost}
        </div>
      </div>
    </div>
  )
}
