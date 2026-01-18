import { SkillCardWithGranularI18n } from './SkillCard'
import type { IdentitySkillEntry, Uptie } from '@/types/IdentityTypes'

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
  /** Whether skill 3 is locked (uptie < 3) */
  isSkill3Locked: boolean
  /** Function to convert slot to numeric slot number */
  getSkillSlotNumber: (slot: SkillSlot) => number
}

/**
 * Skill section with granular i18n Suspense.
 * Structure stays visible, only name/description suspends.
 */
export function SkillsSectionI18n({
  id,
  skills,
  activeSkillSlot,
  uptieLevel,
  isSkill3Locked,
  getSkillSlotNumber,
}: SkillsSectionI18nProps) {
  const isCurrentSkillLocked = activeSkillSlot === 'skill3' && isSkill3Locked

  return (
    <div className="border rounded divide-y">
      {skills[activeSkillSlot].map((skill) => {
        const displayUptie = isCurrentSkillLocked ? 3 as Uptie : uptieLevel

        return (
          <SkillCardWithGranularI18n
            key={skill.id}
            identityId={id}
            skillSlot={getSkillSlotNumber(activeSkillSlot)}
            skillEntry={skill}
            uptie={displayUptie}
            isLocked={isCurrentSkillLocked}
          />
        )
      })}
    </div>
  )
}
