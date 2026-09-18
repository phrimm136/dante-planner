import { IconFilter } from './IconFilter'
import { ATK_TYPES, type AtkType } from '@/shared/gameData'
import { getAttackTypeIconPath } from '@/shared/assets'

interface AttackTypeFilterProps {
  selected: Set<AtkType>
  onSelectionChange: (types: Set<AtkType>) => void
}

export function AttackTypeFilter({ selected, onSelectionChange }: AttackTypeFilterProps) {
  return (
    <IconFilter
      options={ATK_TYPES}
      selectedOptions={selected}
      onSelectionChange={onSelectionChange}
      getIconPath={getAttackTypeIconPath}
    />
  )
}
