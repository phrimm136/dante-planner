import { CompactIconFilter } from './CompactIconFilter'
import { AFFINITIES, type SkillAttributeType } from '@/shared/gameData'
import { getAffinityIconPath } from '@/shared/assets'

interface CompactSkillAttributeFilterProps {
  selected: Set<SkillAttributeType>
  onSelectionChange: (attributes: Set<SkillAttributeType>) => void
}

/**
 * Compact skill attribute icon filter for filter sidebar
 * 7 affinity icons displayed in a 7-column grid (1 row)
 *
 * Pattern: Wraps CompactIconFilter like SkillAttributeFilter wraps IconFilter
 */
export function CompactSkillAttributeFilter({
  selected,
  onSelectionChange,
}: CompactSkillAttributeFilterProps) {
  return (
    <CompactIconFilter
      options={AFFINITIES}
      selectedOptions={selected}
      onSelectionChange={onSelectionChange}
      getIconPath={getAffinityIconPath}
    />
  )
}
