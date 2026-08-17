import { IconFilter } from './IconFilter'
import { EGO_TYPES } from '@/shared/gameData'
import { getEGOTypeIconPath } from '@/shared/assets'

interface EGOTypeFilterProps {
  selected: Set<string>
  onSelectionChange: (types: Set<string>) => void
}

/**
 * EGO type icon filter for filter sidebar
 * 5 EGO type icons displayed in a 7-column grid (matches keyword/skill attribute filters)
 * Icons stay small and left-aligned, not stretching to fill container width
 *
 */
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
