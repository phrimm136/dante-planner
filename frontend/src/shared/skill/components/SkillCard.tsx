import { Suspense, useState, type ReactNode } from 'react'

import { getLockIconPath } from '@/shared/assets'
import { type SkillAttributeType } from '@/shared/gameData'
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
  skillData: SkillCardData
  coinString: string
  skillImagePath: string
  skillTier: number
  nameSlot: ReactNode
  descriptionSlot: ReactNode
  isDefenseSkill?: boolean
  sanityCost?: number
  isLocked?: boolean
}

export function SkillCard({
  skillData,
  coinString,
  skillImagePath,
  skillTier,
  nameSlot,
  descriptionSlot,
  isDefenseSkill = false,
  sanityCost,
  isLocked = false,
}: SkillCardProps) {
  const [showMissing, setShowMissing] = useState(false)

  const attributeType = (skillData.attributeType ?? 'NEUTRAL') as SkillAttributeType
  const atkType = skillData.atkType
  const basePower = skillData.defaultValue ?? 0
  const coinPower = skillData.scale ?? 0

  const card = (
    <SkillCardLayout
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
          coinString={coinString}
          nameSlot={nameSlot}
          isDefenseSkill={isDefenseSkill}
          sanityCost={sanityCost}
        />
      }
      description={<Suspense fallback={<SkillDescriptionSkeleton />}>{descriptionSlot}</Suspense>}
    />
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
