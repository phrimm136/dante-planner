import { MAX_LEVEL } from '@/lib/constants'
import { CoinDisplay } from '@/components/identity/CoinDisplay'
import type { EGOSkillData } from '@/types/EGOTypes'

interface EGOSkillInfoPanelProps {
  skillName: string
  skillData: EGOSkillData
  threadspin: '3' | '4'
}

/**
 * EGOSkillInfoPanel - Displays EGO skill name and specifications
 *
 * Layout (vertical):
 * 1. Coin EA display
 * 2. Skill name
 * 3. Attack level with icon
 * 4. Attack weight and sanity cost
 */
export function EGOSkillInfoPanel({
  skillName,
  skillData,
  threadspin
}: EGOSkillInfoPanelProps) {
  const { coinEA, LV, sanityCost, threadspins } = skillData

  // Calculate total level (max + skill LV modifier), ensure at least 1
  const totalLevel = Math.max(1, MAX_LEVEL + LV)

  // Get current threadspin data (first element of array)
  const threadspinData = threadspins[threadspin][0]

  return (
    <div className="flex flex-col gap-2">
      {/* Coin EA */}
      <div>
        <CoinDisplay coinEA={coinEA} />
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

        {/* Attack weight */}
        <div className="text-muted-foreground">
          Weight: {threadspinData.atkWeight}
        </div>

        {/* Sanity cost */}
        <div className="text-muted-foreground">
          Sanity: {sanityCost}
        </div>
      </div>
    </div>
  )
}
