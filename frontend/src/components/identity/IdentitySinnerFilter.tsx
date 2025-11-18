import { getSinnerIconPath } from '@/lib/assetPaths'
import { IconFilter } from '@/components/common/IconFilter'
import { SINNERS } from '@/lib/globalConstants'

interface IdentitySinnerFilterProps {
  selectedSinners: Set<string>
  onSelectionChange: (sinners: Set<string>) => void
}

export function IdentitySinnerFilter({
  selectedSinners,
  onSelectionChange,
}: IdentitySinnerFilterProps) {
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
