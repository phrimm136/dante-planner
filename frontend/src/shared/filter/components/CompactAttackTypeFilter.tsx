import { CompactIconFilter } from './CompactIconFilter'
import { ATK_TYPES, type AtkType } from '@/shared/gameData'
import { getAttackTypeIconPath } from '@/shared/assets'

interface CompactAttackTypeFilterProps {
  selected: Set<AtkType>
  onSelectionChange: (types: Set<AtkType>) => void
}

/**
 * Compact attack type icon filter for filter sidebar
 * 3 attack type icons displayed in a 7-column grid (matches keyword/skill attribute filters)
 * Icons stay small and left-aligned, not stretching to fill container width
 *
 * Pattern: Wraps CompactIconFilter like AttackTypeFilter wraps IconFilter
 */
export function CompactAttackTypeFilter({
  selected,
  onSelectionChange,
}: CompactAttackTypeFilterProps) {
  return (
    <CompactIconFilter
      options={ATK_TYPES}
      selectedOptions={selected}
      onSelectionChange={onSelectionChange}
      getIconPath={getAttackTypeIconPath}
    />
  )
}
