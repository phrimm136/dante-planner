import { IconFilter } from './IconFilter'
import { DEF_TYPES, type DefType } from '@/shared/gameData'
import { getDefenseTypeIconPath } from '@/shared/assets'

interface DefenseTypeFilterProps {
  selected: Set<DefType>
  onSelectionChange: (types: Set<DefType>) => void
}

export function DefenseTypeFilter({
  selected,
  onSelectionChange,
}: DefenseTypeFilterProps) {
  return (
    <IconFilter
      options={DEF_TYPES}
      selectedOptions={selected}
      onSelectionChange={onSelectionChange}
      getIconPath={getDefenseTypeIconPath}
    />
  )
}
