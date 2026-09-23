import { orderIdentitySkills } from '@/shared/skill'
import type { SkillMap } from '@/shared/gameData'
import { IdentitySkillCardWithGranularI18n } from './IdentitySkillCard'
import type { Uptie } from '../types/IdentityTypes'

interface SkillsSectionI18nProps {
  id: string
  skills: SkillMap
  uptieLevel: Uptie
  level: number
}

export function SkillsSectionI18n({ id, skills, uptieLevel, level }: SkillsSectionI18nProps) {
  return (
    <div className="border rounded divide-y">
      {orderIdentitySkills(skills).map(({ id: skillId, entry }) => (
        <IdentitySkillCardWithGranularI18n
          key={skillId}
          identityId={id}
          skillId={skillId}
          entry={entry}
          uptie={uptieLevel}
          unitLevel={level}
        />
      ))}
    </div>
  )
}
