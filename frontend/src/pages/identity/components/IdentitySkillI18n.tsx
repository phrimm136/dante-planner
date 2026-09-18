import { IdentitySkillCardWithGranularI18n, getFirstDefinedUptie } from './IdentitySkillCard'
import type { IdentitySkillEntry, Uptie } from '../types/IdentityTypes'

type SkillSlot = 'skill1' | 'skill2' | 'skill3' | 'skillDef'

interface SkillsSectionI18nProps {
  id: string
  skills: Record<SkillSlot, IdentitySkillEntry[]>
  activeSkillSlot: SkillSlot
  uptieLevel: Uptie
  getSkillSlotNumber: (slot: SkillSlot) => number
}

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
