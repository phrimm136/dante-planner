import { Suspense, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { MIN_LEVEL } from '@/shared/gameData'
import { SANITY_INDICATOR_COLORS } from '@/lib/constants'
import { getDisplayFontForNumeric } from '@/lib/utils'
import { StyledNameSkeleton } from '@/shared/gameText'
import {
  getAttackWeightIconPath,
  getAttackLevelIconPath,
  getDefenseLevelIconPath,
} from '@/shared/assets'
import type { SkillCoin } from '@/shared/gameData'
import { skillUpperLayout } from '../lib/skillLayout'
import { CoinDisplay } from './CoinDisplay'

interface SkillInfoPanelData {
  attributeType?: string | undefined
  skillLevelCorrection?: number | undefined
  targetNum?: number | undefined
}

interface SkillInfoPanelWithSuspenseProps {
  skillData: SkillInfoPanelData
  level: number
  coins: readonly SkillCoin[]
  nameSlot: ReactNode
  isDefenseSkill?: boolean
  sanityCost?: number | undefined
}

const LAYOUT = skillUpperLayout()

export function SkillInfoPanelWithSuspense({
  skillData,
  level,
  coins,
  nameSlot,
  isDefenseSkill = false,
  sanityCost,
}: SkillInfoPanelWithSuspenseProps) {
  const { t } = useTranslation('database')
  const skillLevelCorrection = skillData.skillLevelCorrection ?? 0
  const totalLevel = Math.max(MIN_LEVEL, level + skillLevelCorrection)
  const atkWeight = skillData.targetNum ?? 1

  return (
    <>
      <div data-slot="skill-coins" className="absolute w-max" style={LAYOUT.coins}>
        <CoinDisplay coins={coins} />
      </div>

      <div data-slot="skill-name-area" className="absolute" style={LAYOUT.nameArea}>
        <Suspense
          fallback={
            <StyledNameSkeleton attributeType={skillData.attributeType} plate={LAYOUT.namePlate} />
          }
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
      <span
        data-slot="skill-level"
        className="absolute underline leading-none whitespace-nowrap"
        style={{ ...LAYOUT.levelText, fontFamily: getDisplayFontForNumeric() }}
      >
        {totalLevel}
      </span>

      <span
        data-slot="skill-target-label"
        className="absolute flex items-center whitespace-nowrap text-yellow-400"
        style={LAYOUT.targetLabel}
      >
        {t('identity.atkWeight')}
      </span>
      <div
        data-slot="skill-target-count"
        className="absolute flex items-center gap-1"
        style={LAYOUT.targetCount}
      >
        {Array.from({ length: atkWeight }).map((_, index) => (
          <img key={index} src={getAttackWeightIconPath()} alt="" className="size-[1em]" />
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
