import { SkillImageComposite } from './SkillImageComposite'
import { SkillInfoPanel } from './SkillInfoPanel'
import { SkillDescription } from './SkillDescription'
import type { SkillData, SkillI18nData } from '@/types/IdentityTypes'

interface SkillCardProps {
  identityId: string
  skillSlot: number
  variantIndex: number
  skillData: SkillData
  skillI18n: SkillI18nData
  skillEA: number
  isUptie4?: boolean
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
  skillI18n,
  skillEA,
  isUptie4 = false,
}: SkillCardProps) {
  const isDefenseSkill = !skillData.atkType

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
          isUptie4={isUptie4}
        />

        {/* Skill info panel */}
        <SkillInfoPanel
          skillName={skillI18n.name}
          skillData={skillData}
          skillEA={skillEA}
          isDefenseSkill={isDefenseSkill}
        />
      </div>

      {/* Bottom section: Skill description */}
      <SkillDescription skillI18n={skillI18n} />
    </div>
  )
}
