import { getStatusEffectIconPath } from '@/lib/identityUtils'
import { IconFilter } from './IconFilter'

const STATUS_EFFECTS = [
  'burn',
  'bleed',
  'tremor',
  'rupture',
  'sinking',
  'poise',
  'charge',
] as const

interface IdentityKeywordFilterProps {
  selectedKeywords: Set<string>
  onSelectionChange: (keywords: Set<string>) => void
}

export function IdentityKeywordFilter({
  selectedKeywords,
  onSelectionChange,
}: IdentityKeywordFilterProps) {
  const getIconPath = (keyword: string) => getStatusEffectIconPath(`[${keyword}]`)

  return (
    <IconFilter
      options={STATUS_EFFECTS}
      selectedOptions={selectedKeywords}
      onSelectionChange={onSelectionChange}
      getIconPath={getIconPath}
      clearLabel="Clear all filters"
    />
  )
}
