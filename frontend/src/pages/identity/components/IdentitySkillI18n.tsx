import { IdentitySkillCardWithGranularI18n, getFirstDefinedUptie } from './IdentitySkillCard'
import type { IdentitySkillEntry, Uptie } from '../types/IdentityTypes'

type SkillSlot = 'skill1' | 'skill2' | 'skill3' | 'skillDef'

interface SkillsSectionI18nProps {
  /** Identity ID for i18n lookup */
  id: string
  /** Skills data keyed by slot */
  skills: Record<SkillSlot, IdentitySkillEntry[]>
  /** Currently active skill slot */
  activeSkillSlot: SkillSlot
  /** Current uptie level (1-4) */
  uptieLevel: Uptie
  /** Function to convert slot to numeric slot number */
  getSkillSlotNumber: (slot: SkillSlot) => number
}

/**
 * Skill section with granular i18n Suspense.
 * Structure stays visible, only name/description suspends.
 *
 * Locking is per-skill: a card whose first defined uptie is above the
 * currently selected uptie renders at its first defined level with a
 * lock overlay. This generalizes the old "skill3 unlocks at uptie 3"
 * rule to skills like 1061405 (uptie 2) and 1071505 (uptie 4).
 */
export function SkillsSectionI18n({
  id,
  skills,
  activeSkillSlot,
  uptieLevel,
  getSkillSlotNumber,
}: SkillsSectionI18nProps) {
  return (
    <div className="border rounded divide-y">
      {skills[activeSkillSlot].map((skill) => {
        const firstDefinedUptie = getFirstDefinedUptie(skill.skillData)
        const isLocked = uptieLevel < firstDefinedUptie
        const displayUptie = isLocked ? firstDefinedUptie : uptieLevel

        return (
          <IdentitySkillCardWithGranularI18n
            key={skill.id}
            identityId={id}
            skillSlot={getSkillSlotNumber(activeSkillSlot)}
            skillEntry={skill}
            uptie={displayUptie}
            isLocked={isLocked}
          />
        )
      })}
    </div>
  )
}
