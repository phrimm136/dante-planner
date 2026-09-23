import { orderEgoSkills } from '@/shared/skill'
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
    <div className="border rounded divide-y">
      {orderEgoSkills(skills).map(({ id: skillId, entry }) => (
        <EGOSkillCardWithGranularI18n
          key={skillId}
          egoId={egoId}
          skillId={skillId}
          entry={entry}
          threadspin={threadspin}
        />
      ))}
    </div>
  )
}
