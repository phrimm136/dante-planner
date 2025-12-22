import { memo } from 'react'
import { getSinnerIconPath } from '@/lib/assetPaths'
import { IconFilter } from '@/components/common/IconFilter'
import { SINNERS } from '@/lib/constants'

interface SinnerFilterProps {
  selectedSinners: Set<string>
  onSelectionChange: (sinners: Set<string>) => void
}

export const SinnerFilter = memo(function SinnerFilter({
  selectedSinners,
  onSelectionChange,
}: SinnerFilterProps) {
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
})
