import { EGOSkillCardWithGranularI18n } from './EGOSkillCard'
import type { EGOSkillEntry, Threadspin } from '@/types/EGOTypes'

interface SkillsSectionI18nProps {
  /** EGO ID for skill image paths */
  egoId: string
  /** Skill type (awakening or erosion) */
  skillType: 'awaken' | 'erosion'
  /** Skills data for current skill type */
  skills: EGOSkillEntry[]
  /** Current threadspin level (1-4) */
  threadspin: Threadspin
}

/**
 * EGO Skill section with granular i18n Suspense.
 * Structure stays visible, only name/description suspends per card.
 * Each card fetches its own i18n data and suspends independently.
 */
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
