import { Suspense, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import {
  SANITY_INDICATOR_COLORS,
  TARGET_LABEL_GEO_SHIFT_EM,
  UNIT_PANEL_COLORS,
} from '@/lib/constants'
import { getDisplayFontForNumeric } from '@/lib/utils'
import { NamePlateSkeleton, PANEL_LEVEL_SHADOW } from '@/shared/gameText'
import {
  getAttackWeightIconPath,
  getAttackLevelIconPath,
  getDefenseLevelIconPath,
} from '@/shared/assets'
import type { SkillCoin } from '@/shared/gameData'
import { skillLevelAt } from '../lib/skillLevel'
import { skillUpperLayout } from '../lib/skillLayout'
import { CoinDisplay } from './CoinDisplay'

interface SkillInfoPanelData {
  attributeType?: string | undefined
  skillLevelCorrection?: number | undefined
  targetNum?: number | undefined
}

interface SkillInfoPanelWithSuspenseProps {
  skillData: SkillInfoPanelData
  coins: readonly SkillCoin[]
  nameSlot: ReactNode
  unitLevel: number | undefined
  isDefenseSkill?: boolean
  sanityCost?: number | undefined
}

const LAYOUT = skillUpperLayout()

export function SkillInfoPanelWithSuspense({
  skillData,
  coins,
  nameSlot,
  unitLevel,
  isDefenseSkill = false,
  sanityCost,
}: SkillInfoPanelWithSuspenseProps) {
  const { t, i18n } = useTranslation('database')
  const totalLevel = skillLevelAt(unitLevel, skillData.skillLevelCorrection ?? 0)
  const atkWeight = skillData.targetNum ?? 1

  return (
    <>
      <div data-slot="skill-coins" className="absolute w-max" style={LAYOUT.coins}>
        <CoinDisplay coins={coins} />
      </div>

      <div data-slot="skill-name-area" className="absolute" style={LAYOUT.nameArea}>
        <Suspense
          fallback={<NamePlateSkeleton kind="skill" attributeType={skillData.attributeType} />}
        >
          {nameSlot}
        </Suspense>
      </div>

      <img
        src={isDefenseSkill ? getDefenseLevelIconPath() : getAttackLevelIconPath()}
        alt={isDefenseSkill ? 'Defense' : 'Attack'}
        data-slot="skill-level-icon"
        className="absolute"
        style={LAYOUT.levelIcon}
      />
      {totalLevel !== undefined && (
        <span
          data-slot="skill-level"
          className="absolute underline leading-none whitespace-nowrap"
          style={{
            ...LAYOUT.levelText,
            textDecorationColor: UNIT_PANEL_COLORS.LEVEL_UNDERLINE,
            textShadow: PANEL_LEVEL_SHADOW,
            fontFamily: getDisplayFontForNumeric(),
          }}
        >
          {totalLevel}
        </span>
      )}

      <span
        data-slot="skill-target-label"
        className="absolute flex items-center whitespace-nowrap"
        style={{
          ...LAYOUT.targetLabel,
          color: UNIT_PANEL_COLORS.TARGET_LABEL,
          transform: `translateY(${String(TARGET_LABEL_GEO_SHIFT_EM[i18n.language] ?? 0)}em)`,
        }}
      >
        {t('identity.atkWeight')}
      </span>
      <div
        data-slot="skill-target-count"
        className="absolute flex items-center"
        style={LAYOUT.targetCount}
      >
        {Array.from({ length: atkWeight }).map((_, index) => (
          <img key={index} src={getAttackWeightIconPath()} alt="" style={LAYOUT.targetSquare} />
        ))}
      </div>

      {sanityCost !== undefined && (
        <div
          data-slot="skill-sanity-cost"
          className="absolute whitespace-nowrap"
          style={{ ...LAYOUT.sanityCost, color: SANITY_INDICATOR_COLORS.INCREMENT }}
        >
          {t('ego.sanityCost')} {sanityCost}
        </div>
      )}
    </>
  )
}
