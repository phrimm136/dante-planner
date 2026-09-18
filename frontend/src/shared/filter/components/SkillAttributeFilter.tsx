import { IconFilter } from './IconFilter'
import { AFFINITIES, type SkillAttributeType } from '@/shared/gameData'
import { getAffinityIconPath } from '@/shared/assets'

interface SkillAttributeFilterProps {
  selected: Set<SkillAttributeType>
  onSelectionChange: (attributes: Set<SkillAttributeType>) => void
}

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
