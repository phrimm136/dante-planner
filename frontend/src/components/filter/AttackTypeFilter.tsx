import { IconFilter } from '@/components/filter/IconFilter'
import { ATK_TYPES } from '@/lib/constants'
import { getAttackTypeIconPath } from '@/lib/assetPaths'

interface AttackTypeFilterProps {
  selectedTypes: Set<string>
  onSelectionChange: (types: Set<string>) => void
}

/**
 * Attack type icon filter for filtering by attack types
 * 3 icons: SLASH, PENETRATE, HIT
 *
 * Pattern: Wraps IconFilter like SinnerFilter.tsx
 */
export function AttackTypeFilter({
  selectedTypes,
  onSelectionChange,
}: AttackTypeFilterProps) {
  return (
    <IconFilter
      options={ATK_TYPES}
      selectedOptions={selectedTypes}
      onSelectionChange={onSelectionChange}
      getIconPath={getAttackTypeIconPath}
    />
  )
}
