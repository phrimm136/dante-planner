import { Suspense, useState, type ReactNode } from 'react'

import { getLockIconPath } from '@/shared/assets'
import { type SkillAttributeType } from '@/shared/gameData'
import type { ImportancePalette, SkillCoin } from '@/shared/gameData'
import { ImportanceDecoration, hasImportance } from '@/shared/importance'
import { SkillImageComposite } from './SkillImageComposite'
import { SkillCardLayout } from './SkillCardLayout'
import { SkillInfoPanelWithSuspense } from './SkillInfoPanel'
import { SkillDescriptionSkeleton } from './SkillDescription'
import { skillLowerLayout } from '../lib/skillLayout'

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
  nameSlot: ReactNode
  descriptionSlot: ReactNode
  unitLevel: number | undefined
  isDefenseSkill?: boolean
  sanityCost?: number
  isLocked?: boolean
  importanceLevel: number
  palette: ImportancePalette
  importancePosition?: 'unlock' | 'base'
  missingImage?: 'placeholder' | 'frame'
}

export function SkillCard({
  skillId,
  skillData,
  coins,
  skillImagePath,
  skillTier,
  nameSlot,
  descriptionSlot,
  unitLevel,
  isDefenseSkill = false,
  sanityCost,
  isLocked = false,
  importanceLevel,
  palette,
  importancePosition = 'unlock',
  missingImage = 'placeholder',
}: SkillCardProps) {
  const [showMissing, setShowMissing] = useState(false)
  const frameOnly = showMissing && missingImage === 'frame'

  const attributeType = (skillData.attributeType ?? 'NEUTRAL') as SkillAttributeType
  const atkType = skillData.atkType
  const basePower = skillData.defaultValue ?? 0
  const coinPower = skillData.scale ?? 0
  const decorated = hasImportance(importanceLevel)

  const layout = (
    <SkillCardLayout
      skillId={skillId}
      hasImportance={decorated}
      imageComposite={
        <SkillImageComposite
          skillImagePath={frameOnly ? undefined : skillImagePath}
          attributeType={attributeType}
          skillTier={skillTier}
          atkType={atkType}
          basePower={basePower}
          coinPower={coinPower}
          onImageError={() => setShowMissing(true)}
          showMissingPlaceholder={showMissing && !frameOnly}
        />
      }
      infoPanel={
        <SkillInfoPanelWithSuspense
          skillData={skillData}
          coins={coins}
          nameSlot={nameSlot}
          unitLevel={unitLevel}
          isDefenseSkill={isDefenseSkill}
          sanityCost={sanityCost}
        />
      }
      description={<Suspense fallback={<SkillDescriptionSkeleton />}>{descriptionSlot}</Suspense>}
    />
  )

  const card = decorated ? (
    <div className="relative isolate @container">
      {layout}
      <ImportanceDecoration
        level={importanceLevel}
        palette={palette}
        variant="skill"
        position={importancePosition}
      />
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
        data-slot="skill-lock"
        className="absolute -z-10 object-contain brightness-20"
        style={skillLowerLayout(decorated).lock}
      />
    </div>
  )
}
