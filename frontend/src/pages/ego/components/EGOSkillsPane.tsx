import { useTranslation } from 'react-i18next'

import { SkillTabButton } from '@/pages/identity'
import { SkillsSectionI18n } from './EGOSkillI18n'

import type { EGOData, EgoSkillType, Threadspin } from '../types/EGOTypes'

interface EGOSkillsPaneProps {
  /** EGO ID for skill image paths */
  id: string
  /** Skills data keyed by skill type */
  skills: EGOData['skills']
  /** Current threadspin level */
  threadspinLevel: Threadspin
  /** Selected skill type (controlled) */
  skillType: EgoSkillType
  onSkillTypeChange: (skillType: EgoSkillType) => void
}

/** First skill data entry that declares an attribute type, for tab colouring. */
function getSkillAttributeType(
  skills: EGOData['skills'],
  skillTypeKey: EgoSkillType,
): string | undefined {
  const skillsForType = skills[skillTypeKey]
  if (!skillsForType || skillsForType.length === 0) return undefined

  for (const entry of skillsForType) {
    for (const data of entry.skillData) {
      if (data.attributeType) {
        return data.attributeType
      }
    }
  }
  return undefined
}

/**
 * Skill type selector and skill panel.
 */
export function EGOSkillsPane({
  id,
  skills,
  threadspinLevel,
  skillType,
  onSkillTypeChange,
}: EGOSkillsPaneProps) {
  const { t } = useTranslation(['database', 'common'])

  const hasErosion = skills.erosion && skills.erosion.length > 0

  return (
    <div className="space-y-4">
      {/* Skill Type Selector */}
      <div className="flex gap-2">
        <SkillTabButton
          attributeType={getSkillAttributeType(skills, 'awaken')}
          label={t('skill.awakening')}
          onClick={() => {
            onSkillTypeChange('awaken')
          }}
          isActive={skillType === 'awaken'}
        />
        {hasErosion && (
          <SkillTabButton
            attributeType={getSkillAttributeType(skills, 'erosion')}
            label={t('skill.corrosion')}
            onClick={() => {
              onSkillTypeChange('erosion')
            }}
            isActive={skillType === 'erosion'}
          />
        )}
      </div>

      {/* Skill Display - uses internal granular Suspense for name/description */}
      <SkillsSectionI18n
        egoId={id}
        skillType={skillType}
        skills={skills[skillType]}
        threadspin={threadspinLevel}
      />
    </div>
  )
}
