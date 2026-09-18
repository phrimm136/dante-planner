import { IconFilter } from './IconFilter'
import { EGO_TYPES } from '@/shared/gameData'
import { getEGOTypeIconPath } from '@/shared/assets'

interface EGOTypeFilterProps {
  selected: Set<string>
  onSelectionChange: (types: Set<string>) => void
}

export function EGOTypeFilter({ selected, onSelectionChange }: EGOTypeFilterProps) {
  return (
    <IconFilter
      options={EGO_TYPES}
      selectedOptions={selected}
      onSelectionChange={onSelectionChange}
      getIconPath={getEGOTypeIconPath}
    />
  )
}
