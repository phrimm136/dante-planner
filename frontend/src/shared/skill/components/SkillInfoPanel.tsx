import { Suspense, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import {
  SANITY_INDICATOR_COLORS,
  SKILL_UPPER_UNITS,
  TARGET_LABEL_GEO_SHIFT_EM,
  UNIT_PANEL_COLORS,
  UNIT_PANEL_UNDERLAY_COLORS,
  UNIT_PANEL_UNDERLAYS_EM,
} from '@/lib/constants'
import { clientLength, getDisplayFontForLanguage, getDisplayFontForNumeric } from '@/lib/utils'
import { NamePlateSkeleton, PANEL_LEVEL_SHADOW, emUnderlayShadow } from '@/shared/gameText'
import { getAttackLevelIconPath, getDefenseLevelIconPath } from '@/shared/assets'
import type { SkillCoin } from '@/shared/gameData'
import { skillLevelAt } from '../lib/skillLevel'
import { skillUpperLayout, targetCountFontUnits } from '../lib/skillLayout'
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

const TARGET_COUNT_SHADOW = emUnderlayShadow(
  UNIT_PANEL_UNDERLAYS_EM.NAME.KR,
  UNIT_PANEL_UNDERLAY_COLORS.NAME,
)

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
      <span
        data-slot="skill-target-count"
        className="absolute font-bold whitespace-nowrap"
        style={{
          ...LAYOUT.targetCount,
          ...getDisplayFontForLanguage('KR'),
          fontSize: clientLength(targetCountFontUnits(atkWeight)),
          color: UNIT_PANEL_COLORS.TARGET_LABEL,
          textShadow: TARGET_COUNT_SHADOW,
        }}
      >
        {SKILL_UPPER_UNITS.TARGET.COUNT_GLYPH.repeat(atkWeight)}
      </span>

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
