import { IconFilter } from '@/components/filter/IconFilter'
import { AFFINITIES } from '@/lib/constants'
import { getAffinityIconPath } from '@/lib/assetPaths'

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
