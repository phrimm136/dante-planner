import { IconFilter } from './IconFilter'
import { ATK_TYPES, type AtkType } from '@/shared/gameData'
import { getAttackTypeIconPath } from '@/shared/assets'

interface AttackTypeFilterProps {
  selected: Set<AtkType>
  onSelectionChange: (types: Set<AtkType>) => void
}

/**
 * Attack type icon filter for filter sidebar
 * 3 attack type icons displayed in a 7-column grid (matches keyword/skill attribute filters)
 * Icons stay small and left-aligned, not stretching to fill container width
 *
 */
export function AttackTypeFilter({
  selected,
  onSelectionChange,
}: AttackTypeFilterProps) {
  return (
    <IconFilter
      options={ATK_TYPES}
      selectedOptions={selected}
      onSelectionChange={onSelectionChange}
      getIconPath={getAttackTypeIconPath}
    />
  )
}
