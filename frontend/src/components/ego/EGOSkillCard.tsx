import { EGOSkillImageComposite } from './EGOSkillImageComposite'
import { EGOSkillInfoPanel } from './EGOSkillInfoPanel'
import { SkillDescription } from '@/components/identity/SkillDescription'
import type { EGOSkillData, EGOSkillI18n } from '@/types/EGOTypes'

interface EGOSkillCardProps {
  egoId: string
  skillType: 'awakening' | 'corrosion'
  skillData: EGOSkillData
  skillI18n: EGOSkillI18n
  sin: string
  threadspin: '3' | '4'
}

/**
 * EGOSkillCard - Complete EGO skill display card
 *
 * Layout:
 * - Horizontal layout with skill image on left, info panel on right
 * - Skill description below the main layout
 */
export function EGOSkillCard({
  egoId,
  skillType,
  skillData,
  skillI18n,
  sin,
  threadspin,
}: EGOSkillCardProps) {
  const skillName = skillI18n.name
  const skillThreadspinI18n = skillI18n.threadspins[threadspin][0]

  return (
    <div className="border rounded-lg p-4 space-y-3">
      {/* Top section: Image + Info */}
      <div className="flex gap-4">
        {/* Skill image composite */}
        <EGOSkillImageComposite
          egoId={egoId}
          skillType={skillType}
          skillData={skillData}
          sin={sin}
          threadspin={threadspin}
        />

        {/* Skill info panel */}
        <EGOSkillInfoPanel
          skillName={skillName}
          skillData={skillData}
          threadspin={threadspin}
        />
      </div>

      {/* Bottom section: Skill description */}
      <SkillDescription skillI18n={skillThreadspinI18n} />
    </div>
  )
}
