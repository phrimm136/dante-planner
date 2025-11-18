import { SkillImageComposite } from './SkillImageComposite'
import { SkillInfoPanel } from './SkillInfoPanel'
import { SkillDescription } from './SkillDescription'
import { SkillCardLayout } from '@/components/common/SkillCardLayout'
import type { SkillData, SkillI18nData, Uptie } from '@/types/IdentityTypes'

interface SkillCardProps {
  identityId: string
  skillSlot: number
  variantIndex: number
  skillData: SkillData
  skillI18nData: SkillI18nData
  uptie: Uptie
}

/**
 * SkillCard - Complete skill display card
 *
 * Layout:
 * - Horizontal layout with skill image on left, info panel on right
 * - Skill description below the main layout
 */
export function SkillCard({
  identityId,
  skillSlot,
  variantIndex,
  skillData,
  skillI18nData,
  uptie,
}: SkillCardProps) {
  const isDefenseSkill = !skillData.atkType
  const skillEA = skillData.quantity
  const skillName = skillI18nData.name
  const uptieI18nData = skillI18nData.upties[uptie]

  return (
    <SkillCardLayout
      imageComposite={
        <SkillImageComposite
          identityId={identityId}
          skillSlot={skillSlot}
          variantIndex={variantIndex}
          skillData={skillData}
          uptie={uptie}
        />
      }
      infoPanel={
        <SkillInfoPanel
          skillName={skillName}
          skillData={skillData}
          skillEA={skillEA}
          isDefenseSkill={isDefenseSkill}
          uptie={uptie}
        />
      }
      description={<SkillDescription uptieI18nData={uptieI18nData} />}
    />
  )
}
