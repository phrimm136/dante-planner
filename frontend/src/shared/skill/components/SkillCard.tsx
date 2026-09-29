import { Suspense, useState, type ReactNode } from 'react'

import { getLockIconPath } from '@/shared/assets'
import { type SkillAttributeType } from '@/shared/gameData'
import type { ImportancePalette, SkillCoin } from '@/shared/gameData'
import { ImportanceDecoration, hasImportance } from '@/shared/importance'
import { SkillImageComposite } from './SkillImageComposite'
import { SkillCardLayout } from './SkillCardLayout'
import { SkillInfoPanelWithSuspense } from './SkillInfoPanel'
import { SkillDescriptionSkeleton } from './SkillDescription'

interface SkillCardData {
  attributeType?: string | undefined
  atkType?: string | undefined
  defaultValue?: number | undefined
  scale?: number | undefined
  skillLevelCorrection?: number | undefined
  targetNum?: number | undefined
}

interface SkillCardProps {
  skillId: string
  skillData: SkillCardData
  coins: readonly SkillCoin[]
  skillImagePath: string | undefined
  skillTier: number
  level: number
  nameSlot: ReactNode
  descriptionSlot: ReactNode
  isDefenseSkill?: boolean
  sanityCost?: number
  isLocked?: boolean
  importanceLevel: number
  palette: ImportancePalette
}

export function SkillCard({
  skillId,
  skillData,
  coins,
  skillImagePath,
  skillTier,
  level,
  nameSlot,
  descriptionSlot,
  isDefenseSkill = false,
  sanityCost,
  isLocked = false,
  importanceLevel,
  palette,
}: SkillCardProps) {
  const [showMissing, setShowMissing] = useState(false)

  const attributeType = (skillData.attributeType ?? 'NEUTRAL') as SkillAttributeType
  const atkType = skillData.atkType
  const basePower = skillData.defaultValue ?? 0
  const coinPower = skillData.scale ?? 0

  const layout = (
    <SkillCardLayout
      skillId={skillId}
      imageComposite={
        <SkillImageComposite
          skillImagePath={skillImagePath}
          attributeType={attributeType}
          skillTier={skillTier}
          atkType={atkType}
          basePower={basePower}
          coinPower={coinPower}
          onImageError={() => setShowMissing(true)}
          showMissingPlaceholder={showMissing}
        />
      }
      infoPanel={
        <SkillInfoPanelWithSuspense
          skillData={skillData}
          level={level}
          coins={coins}
          nameSlot={nameSlot}
          isDefenseSkill={isDefenseSkill}
          sanityCost={sanityCost}
        />
      }
      description={<Suspense fallback={<SkillDescriptionSkeleton />}>{descriptionSlot}</Suspense>}
    />
  )

  const card = hasImportance(importanceLevel) ? (
    <div className="relative isolate">
      {layout}
      <ImportanceDecoration level={importanceLevel} palette={palette} variant="skill" />
    </div>
  ) : (
    layout
  )

  if (!isLocked) return card

  return (
    <div className="relative opacity-50">
      {card}
      <img
        src={getLockIconPath()}
        alt=""
        className="absolute right-8 bottom-8 -z-10 h-30 brightness-20"
      />
    </div>
  )
}
