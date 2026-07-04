import { memo } from 'react'
import { getSinnerIconPath } from '@/shared/assets'
import { areSetsEqual } from '@/lib/setUtils'
import { IconFilter } from './IconFilter'
import { SINNERS } from '@/shared/gameData'

interface SinnerFilterProps {
  selectedSinners: Set<string>
  onSelectionChange: (sinners: Set<string>) => void
}

export const SinnerFilter = memo(
  function SinnerFilter({ selectedSinners, onSelectionChange }: SinnerFilterProps) {
    const getIconPath = (sinner: string) => getSinnerIconPath(sinner)

    return (
      <IconFilter
        options={SINNERS}
        selectedOptions={selectedSinners}
        onSelectionChange={onSelectionChange}
        getIconPath={getIconPath}
      />
    )
  },
  (prev, next) => {
    return areSetsEqual(prev.selectedSinners, next.selectedSinners)
    // onSelectionChange excluded - callback identity changes but behavior is same
  },
)
