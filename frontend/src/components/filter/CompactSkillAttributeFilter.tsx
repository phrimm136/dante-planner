import { CompactIconFilter } from '@/components/filter/CompactIconFilter'
import { AFFINITIES } from '@/lib/constants'
import { getAffinityIconPath } from '@/lib/assetPaths'

interface CompactSkillAttributeFilterProps {
  selectedAttributes: Set<string>
  onSelectionChange: (attributes: Set<string>) => void
}

/**
 * Compact skill attribute icon filter for filter sidebar
 * 7 affinity icons displayed in a 7-column grid (1 row)
 *
 * Pattern: Wraps CompactIconFilter like SkillAttributeFilter wraps IconFilter
 * Uses columns prop for grid stretch sizing (same as CompactSinnerFilter)
 */
export function CompactSkillAttributeFilter({
  selectedAttributes,
  onSelectionChange,
}: CompactSkillAttributeFilterProps) {
  return (
    <CompactIconFilter
      options={AFFINITIES}
      selectedOptions={selectedAttributes}
      onSelectionChange={onSelectionChange}
      getIconPath={getAffinityIconPath}
      columns={7}
    />
  )
}
