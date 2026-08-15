import { Suspense, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { MAX_LEVEL } from '@/shared/gameData'
import { SANITY_INDICATOR_COLORS, SECTION_STYLES } from '@/lib/constants'
import { getDisplayFontForNumeric } from '@/lib/utils'
import { StyledNameSkeleton } from '@/shared/gameText'
import {
  getAttackWeightIconPath,
  getAttackLevelIconPath,
  getDefenseLevelIconPath,
} from '@/shared/assets'
import { CoinDisplay } from './CoinDisplay'

/** Minimal structural shape of the per-level skill stats this panel reads. */
interface SkillInfoPanelData {
  attributeType?: string
  skillLevelCorrection?: number
  targetNum?: number
}

interface SkillInfoPanelWithSuspenseProps {
  skillData: SkillInfoPanelData
  coinString: string
  /**
   * The owning slice's skill-name element. It calls the slice's own detail-i18n
   * hook and suspends inside this panel's boundary, so `shared/skill` stays free
   * of any `@/pages/*` import (sink rule).
   */
  nameSlot: ReactNode
  /** Identity-only: renders the defense level icon instead of attack. EGO skills are always attack. */
  isDefenseSkill?: boolean
  /** EGO-only: sanity (MP) cost. When provided, renders the sanity-cost stat. */
  sanityCost?: number
}

/**
 * SkillInfoPanel with granular i18n Suspense — shared by identity and ego.
 *
 * Layout (vertical):
 * 1. Coin display
 * 2. Skill name (suspends for i18n)
 * 3. Attack/Defense level with icon
 * 4. Attack-weight indicator (game icons) [+ sanity cost when supplied]
 *
 * Domain differences are injected: `isDefenseSkill` picks the level icon,
 * `sanityCost` opts into the ego-only sanity stat.
 */
export function SkillInfoPanelWithSuspense({
  skillData,
  coinString,
  nameSlot,
  isDefenseSkill = false,
  sanityCost,
}: SkillInfoPanelWithSuspenseProps) {
  const { t } = useTranslation('database')
  const skillLevelCorrection = skillData.skillLevelCorrection ?? 0
  const totalLevel = Math.max(1, MAX_LEVEL + skillLevelCorrection)
  const atkWeight = skillData.targetNum ?? 1

  return (
    <div className="flex flex-col -translate-x-5">
      {/* Coin display */}
      <div>
        <CoinDisplay coinEA={coinString} />
      </div>

      {/* Skill name - suspends for i18n */}
      <Suspense fallback={<StyledNameSkeleton attributeType={skillData.attributeType} />}>
        {nameSlot}
      </Suspense>

      {/* Level and stats display - vertical on mobile, horizontal on desktop */}
      <div className="flex flex-col md:flex-row md:items-center gap-1 md:gap-3 text-sm">
        {/* Level */}
        <div className={SECTION_STYLES.LAYOUT.row}>
          <img
            src={isDefenseSkill ? getDefenseLevelIconPath() : getAttackLevelIconPath()}
            alt={isDefenseSkill ? 'Defense' : 'Attack'}
            className="w-11 h-11"
          />
          <span
            className="underline text-[38px] -translate-y-1.5 leading-none"
            style={{ fontFamily: getDisplayFontForNumeric() }}
          >
            {totalLevel}
          </span>
        </div>

        {/* Attack weight indicator (game icons) */}
        <div className="flex items-center gap-2 text-yellow-400">
          <span>{t('identity.atkWeight')}</span>
          <div className="flex gap-1 h-3.5">
            {Array.from({ length: atkWeight }).map((_, index) => (
              <img key={index} src={getAttackWeightIconPath()} alt="" className="w-3 h-3" />
            ))}
          </div>
        </div>

        {/* Sanity cost (ego only) */}
        {sanityCost !== undefined && (
          <div style={{ color: SANITY_INDICATOR_COLORS.INCREMENT }}>
            {t('ego.sanityCost')} {sanityCost}
          </div>
        )}
      </div>
    </div>
  )
}
