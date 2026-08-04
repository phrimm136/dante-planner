import { CompactIconFilter } from './CompactIconFilter'
import { EGO_TYPES } from '@/shared/gameData'
import { getEGOTypeIconPath } from '@/shared/assets'

interface CompactEGOTypeFilterProps {
  selected: Set<string>
  onSelectionChange: (types: Set<string>) => void
}

/**
 * Compact EGO type icon filter for filter sidebar
 * 5 EGO type icons displayed in a 7-column grid (matches keyword/skill attribute filters)
 * Icons stay small and left-aligned, not stretching to fill container width
 *
 * Pattern: Wraps CompactIconFilter like AttackTypeFilter wraps IconFilter
 */
export function CompactEGOTypeFilter({ selected, onSelectionChange }: CompactEGOTypeFilterProps) {
  return (
    <CompactIconFilter
      options={EGO_TYPES}
      selectedOptions={selected}
      onSelectionChange={onSelectionChange}
      getIconPath={getEGOTypeIconPath}
    />
  )
}
