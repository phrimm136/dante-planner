import { CompactIconFilter } from '@/components/filter/CompactIconFilter'
import { DEF_TYPES, type DefType } from '@/lib/constants'
import { getDefenseTypeIconPath } from '@/lib/assetPaths'

interface CompactDefenseTypeFilterProps {
  selectedTypes: Set<DefType>
  onSelectionChange: (types: Set<DefType>) => void
}

export function CompactDefenseTypeFilter({
  selectedTypes,
  onSelectionChange,
}: CompactDefenseTypeFilterProps) {
  return (
    <CompactIconFilter
      options={DEF_TYPES}
      selectedOptions={selectedTypes}
      onSelectionChange={onSelectionChange}
      getIconPath={getDefenseTypeIconPath}
      columns={7}
    />
  )
}
