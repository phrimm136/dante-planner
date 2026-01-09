import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { useEGODetailI18n } from '@/hooks/useEGODetailData'
import { MAX_LEVEL } from '@/lib/constants'
import { CoinDisplay } from '@/components/identity/CoinDisplay'
import { StyledSkillName, StyledNameSkeleton } from '@/components/common/StyledName'
import type { EGOSkillDataEntry } from '@/types/EGOTypes'

interface EGOSkillInfoPanelProps {
  skillName: string
  skillData: EGOSkillDataEntry
  coinString: string
}

interface EGOSkillInfoPanelWithSuspenseProps {
  egoId: string
  skillId: number
  skillData: EGOSkillDataEntry
  coinString: string
}

/**
 * EGOSkillInfoPanel - Displays EGO skill name and specifications
 *
 * Layout (vertical):
 * 1. Coin display
 * 2. Skill name with styled background
 * 3. Attack level with icon
 * 4. Attack weight and sanity cost
 */
export function EGOSkillInfoPanel({
  skillName,
  skillData,
  coinString,
}: EGOSkillInfoPanelProps) {
  const { t } = useTranslation('database')
  const skillLevelCorrection = skillData.skillLevelCorrection ?? 0
  const atkWeight = skillData.targetNum ?? 1
  const sanityCost = skillData.mpUsage ?? 0

  // Calculate total level (max + skill level correction), ensure at least 1
  const totalLevel = Math.max(1, MAX_LEVEL + skillLevelCorrection)

  return (
    <div className="flex flex-col gap-2">
      {/* Coin display */}
      <div>
        <CoinDisplay coinEA={coinString} />
      </div>

      {/* Skill name with styled background */}
      <StyledSkillName name={skillName} attributeType={skillData.attributeType} />

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

        {/* Attack weight indicator */}
        <div className="flex items-center gap-2 text-yellow-400">
          <span>{t('identity.atkWeight')}</span>
          <span>{'■'.repeat(atkWeight)}</span>
        </div>

        {/* Sanity cost */}
        <div className="text-muted-foreground">
          Sanity: {sanityCost}
        </div>
      </div>
    </div>
  )
}

// =============================================================================
// Granular I18n Version
// =============================================================================

/**
 * EGOSkillInfoPanel with granular i18n Suspense.
 * Structure (coins, stats) stays visible, only name suspends.
 */
export function EGOSkillInfoPanelWithSuspense({
  egoId,
  skillId,
  skillData,
  coinString,
}: EGOSkillInfoPanelWithSuspenseProps) {
  const { t } = useTranslation('database')
  const skillLevelCorrection = skillData.skillLevelCorrection ?? 0
  const atkWeight = skillData.targetNum ?? 1
  const sanityCost = skillData.mpUsage ?? 0
  const totalLevel = Math.max(1, MAX_LEVEL + skillLevelCorrection)

  return (
    <div className="flex flex-col gap-2">
      {/* Coin display */}
      <div>
        <CoinDisplay coinEA={coinString} />
      </div>

      {/* Skill name - suspends for i18n */}
      <Suspense fallback={<StyledNameSkeleton attributeType={skillData.attributeType} />}>
        <SkillNameContent egoId={egoId} skillId={skillId} attributeType={skillData.attributeType} />
      </Suspense>

      {/* Level and stats display */}
      <div className="flex items-center gap-3 text-sm">
        <div className="flex items-center gap-1">
          <img
            src="/images/UI/identity/attack.webp"
            alt="Attack"
            className="w-4 h-4"
          />
          <span className="underline">{totalLevel}</span>
        </div>
        <div className="flex items-center gap-2 text-yellow-400">
          <span>{t('identity.atkWeight')}</span>
          <span>{'■'.repeat(atkWeight)}</span>
        </div>
        <div className="text-muted-foreground">
          Sanity: {sanityCost}
        </div>
      </div>
    </div>
  )
}

/**
 * Internal: Fetches and renders skill name with styled formatting.
 */
function SkillNameContent({
  egoId,
  skillId,
  attributeType,
}: {
  egoId: string
  skillId: number
  attributeType?: string
}) {
  const i18n = useEGODetailI18n(egoId)
  const skillI18n = i18n.skills[String(skillId)]
  return <StyledSkillName name={skillI18n?.name ?? ''} attributeType={attributeType} />
}
