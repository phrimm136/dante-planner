import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { useIdentityDetailI18n } from '@/hooks/useIdentityDetailData'
import { MAX_LEVEL } from '@/lib/constants'
import { CoinDisplay } from './CoinDisplay'
import { StyledSkillName, StyledNameSkeleton } from '@/components/common/StyledName'
import type { IdentitySkillDataEntry } from '@/types/IdentityTypes'

interface SkillInfoPanelProps {
  skillName: string
  skillData: IdentitySkillDataEntry
  coinString: string
  isDefenseSkill: boolean
}

interface SkillInfoPanelWithSuspenseProps {
  identityId: string
  skillId: number
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
 * 4. Attack weight indicator (offense skills only)
 */
export function SkillInfoPanel({
  skillName,
  skillData,
  coinString,
  isDefenseSkill,
}: SkillInfoPanelProps) {
  const { t } = useTranslation('database')
  const skillLevelCorrection = skillData.skillLevelCorrection ?? 0

  // Calculate total level (max + skill level correction), ensure at least 1
  const totalLevel = Math.max(1, MAX_LEVEL + skillLevelCorrection)

  // Get attack weight (targetNum), default to 1 for offense skills
  const atkWeight = skillData.targetNum ?? 1

  return (
    <div className="flex flex-col gap-2">
      {/* Coin display */}
      <div>
        <CoinDisplay coinEA={coinString} />
      </div>

      {/* Skill name */}
      <StyledSkillName name={skillName} attributeType={skillData.attributeType} />

      {/* Level display and attack weight */}
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

        {/* Attack weight indicator (offense skills only) */}
        {!isDefenseSkill && (
          <div className="flex items-center gap-2 text-yellow-400">
            <span>{t('identity.atkWeight')}</span>
            <span>{'■'.repeat(atkWeight)}</span>
          </div>
        )}
      </div>
    </div>
  )
}

// =============================================================================
// Granular I18n Version
// =============================================================================

/**
 * SkillInfoPanel with granular i18n Suspense.
 * Structure (coins, stats) stays visible, only name suspends.
 */
export function SkillInfoPanelWithSuspense({
  identityId,
  skillId,
  skillData,
  coinString,
  isDefenseSkill,
}: SkillInfoPanelWithSuspenseProps) {
  const { t } = useTranslation('database')
  const skillLevelCorrection = skillData.skillLevelCorrection ?? 0
  const totalLevel = Math.max(1, MAX_LEVEL + skillLevelCorrection)
  const atkWeight = skillData.targetNum ?? 1

  return (
    <div className="flex flex-col gap-2">
      {/* Coin display */}
      <div>
        <CoinDisplay coinEA={coinString} />
      </div>

      {/* Skill name - suspends for i18n */}
      <Suspense fallback={<StyledNameSkeleton attributeType={skillData.attributeType} />}>
        <SkillNameContent identityId={identityId} skillId={skillId} attributeType={skillData.attributeType} />
      </Suspense>

      {/* Level display and attack weight */}
      <div className="flex items-center gap-3 text-sm">
        <div className="flex items-center gap-1">
          <img
            src={isDefenseSkill ? '/images/UI/identity/defense.webp' : '/images/UI/identity/attack.webp'}
            alt={isDefenseSkill ? 'Defense' : 'Attack'}
            className="w-4 h-4"
          />
          <span className="underline">{totalLevel}</span>
        </div>
        {!isDefenseSkill && (
          <div className="flex items-center gap-2 text-yellow-400">
            <span>{t('identity.atkWeight')}</span>
            <span>{'■'.repeat(atkWeight)}</span>
          </div>
        )}
      </div>
    </div>
  )
}

/**
 * Internal: Fetches and renders skill name.
 */
function SkillNameContent({
  identityId,
  skillId,
  attributeType,
}: {
  identityId: string
  skillId: number
  attributeType?: string
}) {
  const i18n = useIdentityDetailI18n(identityId)
  const skillI18n = i18n.skills[String(skillId)]
  return <StyledSkillName name={skillI18n?.name ?? ''} attributeType={attributeType} />
}
