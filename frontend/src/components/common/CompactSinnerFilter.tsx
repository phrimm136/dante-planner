import { getSinnerIconPath } from '@/lib/assetPaths'
import { CompactIconFilter } from '@/components/common/CompactIconFilter'
import { SINNERS } from '@/lib/constants'

interface CompactSinnerFilterProps {
  selectedSinners: Set<string>
  onSelectionChange: (sinners: Set<string>) => void
}

/**
 * Compact sinner icon filter for filter sidebar
 * 12 sinners displayed in a 6-column grid (2 rows)
 *
 * Pattern: Wraps CompactIconFilter like SinnerFilter wraps IconFilter
 */
export function CompactSinnerFilter({
  selectedSinners,
  onSelectionChange,
}: CompactSinnerFilterProps) {
  return (
    <CompactIconFilter
      options={SINNERS}
      selectedOptions={selectedSinners}
      onSelectionChange={onSelectionChange}
      getIconPath={getSinnerIconPath}
      columns={6}
    />
  )
}
