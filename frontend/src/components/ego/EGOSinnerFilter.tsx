import { getSinnerIconPath } from '@/lib/assetPaths'
import { IconFilter } from '@/components/common/IconFilter'
import { SINNERS } from '@/lib/constants'

interface EGOSinnerFilterProps {
  selectedSinners: Set<string>
  onSelectionChange: (sinners: Set<string>) => void
}

export function EGOSinnerFilter({
  selectedSinners,
  onSelectionChange,
}: EGOSinnerFilterProps) {
  const getIconPath = (sinner: string) => getSinnerIconPath(sinner)

  return (
    <IconFilter
      options={SINNERS}
      selectedOptions={selectedSinners}
      onSelectionChange={onSelectionChange}
      getIconPath={getIconPath}
      clearLabel="Clear all filters"
    />
  )
}
