import { SkillImageComposite } from './SkillImageComposite'
import { SkillInfoPanel } from './SkillInfoPanel'
import { SkillDescription } from './SkillDescription'
import type { SkillData, SkillVariantI18n, Uptie } from '@/types/IdentityTypes'

interface SkillCardProps {
  identityId: string
  skillSlot: number
  variantIndex: number
  skillData: SkillData
  skillVariantI18n: SkillVariantI18n
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
  skillVariantI18n,
  uptie,
}: SkillCardProps) {
  const isDefenseSkill = !skillData.atkType
  const skillEA = skillData.quantity
  const skillName = skillVariantI18n.name
  const skillI18n = skillVariantI18n.upties[uptie]

  return (
    <div className="border rounded-lg p-4 space-y-3">
      {/* Top section: Image + Info */}
      <div className="flex gap-4">
        {/* Skill image composite */}
        <SkillImageComposite
          identityId={identityId}
          skillSlot={skillSlot}
          variantIndex={variantIndex}
          skillData={skillData}
          uptie={uptie}
        />

        {/* Skill info panel */}
        <SkillInfoPanel
          skillName={skillName}
          skillData={skillData}
          skillEA={skillEA}
          isDefenseSkill={isDefenseSkill}
          uptie={uptie}
        />
      </div>

      {/* Bottom section: Skill description */}
      <SkillDescription skillI18n={skillI18n} />
    </div>
  )
}
