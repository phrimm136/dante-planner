import { IconFilter } from './IconFilter'
import { AFFINITIES, type SkillAttributeType } from '@/shared/gameData'
import { getAffinityIconPath } from '@/shared/assets'

interface SkillAttributeFilterProps {
  selected: Set<SkillAttributeType>
  onSelectionChange: (attributes: Set<SkillAttributeType>) => void
}

/**
 * Skill attribute icon filter for filter sidebar
 * 7 affinity icons displayed in a 7-column grid (1 row)
 *
 * Pattern: Wraps IconFilter like SkillAttributeFilter wraps IconFilter
 */
export function SkillAttributeFilter({ selected, onSelectionChange }: SkillAttributeFilterProps) {
  return (
    <IconFilter
      options={AFFINITIES}
      selectedOptions={selected}
      onSelectionChange={onSelectionChange}
      getIconPath={getAffinityIconPath}
    />
  )
}
