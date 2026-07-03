import { IconFilter } from './IconFilter'
import { AFFINITIES } from '@/shared/gameData'
import { getAffinityIconPath } from '@/shared/assets'

interface SkillAttributeFilterProps {
  selectedAttributes: Set<string>
  onSelectionChange: (attributes: Set<string>) => void
}

/**
 * Skill attribute icon filter for filtering by sin affinity types
 * 7 affinity icons: CRIMSON, SCARLET, AMBER, SHAMROCK, AZURE, INDIGO, VIOLET
 *
 * Pattern: Wraps IconFilter like SinnerFilter.tsx
 */
export function SkillAttributeFilter({
  selectedAttributes,
  onSelectionChange,
}: SkillAttributeFilterProps) {
  return (
    <IconFilter
      options={AFFINITIES}
      selectedOptions={selectedAttributes}
      onSelectionChange={onSelectionChange}
      getIconPath={getAffinityIconPath}
    />
  )
}
