import { getSinnerIconPath } from '@/lib/identityUtils'
import { IconFilter } from './IconFilter'

const SINNERS = [
  'yiSang',
  'faust',
  'donQuixote',
  'ryoShu',
  'meursault',
  'hongLu',
  'heathcliff',
  'ishmael',
  'rodion',
  'sinclair',
  'outis',
  'gregor',
] as const

interface IdentitySinnerFilterProps {
  selectedSinners: Set<string>
  onSelectionChange: (sinners: Set<string>) => void
}

export function IdentitySinnerFilter({
  selectedSinners,
  onSelectionChange,
}: IdentitySinnerFilterProps) {
  const getIconPath = (sinner: string) => getSinnerIconPath(`[${sinner}]`)

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
