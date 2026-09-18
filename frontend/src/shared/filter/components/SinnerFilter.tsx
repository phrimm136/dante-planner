import { getSinnerIconPath } from '@/shared/assets'
import { IconFilter } from './IconFilter'
import { SINNERS } from '@/shared/gameData'

interface SinnerFilterProps {
  selected: Set<string>
  onSelectionChange: (sinners: Set<string>) => void
}

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
