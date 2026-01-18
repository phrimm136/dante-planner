import { memo } from 'react'
import { getSinnerIconPath } from '@/lib/assetPaths'
import { IconFilter } from '@/components/filter/IconFilter'
import { SINNERS } from '@/lib/constants'

interface SinnerFilterProps {
  selectedSinners: Set<string>
  onSelectionChange: (sinners: Set<string>) => void
}

// Helper to compare Sets by content
function areSetsEqual(a: Set<string>, b: Set<string>): boolean {
  if (a.size !== b.size) return false
  for (const item of a) {
    if (!b.has(item)) return false
  }
  return true
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
    />
  )
}, (prev, next) => {
  return areSetsEqual(prev.selectedSinners, next.selectedSinners)
  // onSelectionChange excluded - callback identity changes but behavior is same
})
