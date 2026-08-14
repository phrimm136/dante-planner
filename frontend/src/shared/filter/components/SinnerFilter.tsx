import { getSinnerIconPath } from '@/shared/assets'
import { IconFilter } from './IconFilter'
import { SINNERS } from '@/shared/gameData'

interface SinnerFilterProps {
  selected: Set<string>
  onSelectionChange: (sinners: Set<string>) => void
}

/**
 * Sinner icon filter for filter sidebar
 * 12 sinners displayed in a 6-column grid (2 rows)
 *
 * Pattern: Wraps IconFilter like SinnerFilter wraps IconFilter
 */
export function SinnerFilter({ selected, onSelectionChange }: SinnerFilterProps) {
  return (
    <IconFilter
      options={SINNERS}
      selectedOptions={selected}
      onSelectionChange={onSelectionChange}
      getIconPath={getSinnerIconPath}
    />
  )
}
