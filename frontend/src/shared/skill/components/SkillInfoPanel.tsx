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

interface SkillInfoPanelData {
  attributeType?: string | undefined
  skillLevelCorrection?: number | undefined
  targetNum?: number | undefined
}

interface SkillInfoPanelWithSuspenseProps {
  skillData: SkillInfoPanelData
  coinString: string
  nameSlot: ReactNode
  isDefenseSkill?: boolean
  sanityCost?: number | undefined
}

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
    <div className="flex grow flex-col pb-4 -ml-5">
      <div>
        <CoinDisplay coinEA={coinString} />
      </div>

      <Suspense fallback={<StyledNameSkeleton attributeType={skillData.attributeType} />}>
        {nameSlot}
      </Suspense>

      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
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

        <div className="flex items-center gap-2 text-yellow-400">
          <span>{t('identity.atkWeight')}</span>
          <div className="flex gap-1 h-3.5">
            {Array.from({ length: atkWeight }).map((_, index) => (
              <img key={index} src={getAttackWeightIconPath()} alt="" className="w-3 h-3" />
            ))}
          </div>
        </div>

        {sanityCost !== undefined && (
          <div style={{ color: SANITY_INDICATOR_COLORS.INCREMENT }}>
            {t('ego.sanityCost')} {sanityCost}
          </div>
        )}
      </div>
    </div>
  )
}
