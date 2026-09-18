import { EGOSkillCardWithGranularI18n } from './EGOSkillCard'
import type { EGOSkillEntry, Threadspin } from '../types/EGOTypes'

interface SkillsSectionI18nProps {
  egoId: string
  skillType: 'awaken' | 'erosion'
  skills: EGOSkillEntry[]
  threadspin: Threadspin
}

export function SkillsSectionI18n({
  egoId,
  skillType,
  skills,
  threadspin,
}: SkillsSectionI18nProps) {
  return (
    <div className="border rounded divide-y">
      {skills.map((skillEntry) => (
        <EGOSkillCardWithGranularI18n
          key={skillEntry.id}
          egoId={egoId}
          skillType={skillType}
          skillEntry={skillEntry}
          threadspin={threadspin}
        />
      ))}
    </div>
  )
}
