import { CompactIconFilter } from './CompactIconFilter'
import { DEF_TYPES, type DefType } from '@/shared/gameData'
import { getDefenseTypeIconPath } from '@/shared/assets'

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
