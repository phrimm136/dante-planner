import { SkillList, orderEgoSkills } from '@/shared/skill'
import type { SkillMap } from '@/shared/gameData'
import { EGOSkillCardWithGranularI18n } from './EGOSkillCard'
import type { Threadspin } from '../types/EGOTypes'

interface SkillsSectionI18nProps {
  egoId: string
  skills: SkillMap
  threadspin: Threadspin
}

export function SkillsSectionI18n({ egoId, skills, threadspin }: SkillsSectionI18nProps) {
  return (
    <SkillList
      skills={orderEgoSkills(skills)}
      renderSkill={({ id: skillId, entry }) => (
        <EGOSkillCardWithGranularI18n
          egoId={egoId}
          skillId={skillId}
          entry={entry}
          threadspin={threadspin}
        />
      )}
    />
  )
}
