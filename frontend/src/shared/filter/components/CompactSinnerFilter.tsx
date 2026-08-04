import { getSinnerIconPath } from '@/shared/assets'
import { CompactIconFilter } from './CompactIconFilter'
import { SINNERS } from '@/shared/gameData'

interface CompactSinnerFilterProps {
  selected: Set<string>
  onSelectionChange: (sinners: Set<string>) => void
}

/**
 * Compact sinner icon filter for filter sidebar
 * 12 sinners displayed in a 6-column grid (2 rows)
 *
 * Pattern: Wraps CompactIconFilter like SinnerFilter wraps IconFilter
 */
export function CompactSinnerFilter({ selected, onSelectionChange }: CompactSinnerFilterProps) {
  return (
    <CompactIconFilter
      options={SINNERS}
      selectedOptions={selected}
      onSelectionChange={onSelectionChange}
      getIconPath={getSinnerIconPath}
    />
  )
}
