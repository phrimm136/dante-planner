import { CompactIconFilter } from '@/components/common/CompactIconFilter'
import { ATK_TYPES } from '@/lib/constants'
import { getAttackTypeIconPath } from '@/lib/assetPaths'

interface CompactAttackTypeFilterProps {
  selectedTypes: Set<string>
  onSelectionChange: (types: Set<string>) => void
}

/**
 * Compact attack type icon filter for filter sidebar
 * 3 attack type icons displayed in a 7-column grid (matches keyword/skill attribute filters)
 * Icons stay small and left-aligned, not stretching to fill container width
 *
 * Pattern: Wraps CompactIconFilter like AttackTypeFilter wraps IconFilter
 * Uses columns={7} for consistency with CompactKeywordFilter and CompactSkillAttributeFilter
 */
export function CompactAttackTypeFilter({
  selectedTypes,
  onSelectionChange,
}: CompactAttackTypeFilterProps) {
  return (
    <CompactIconFilter
      options={ATK_TYPES}
      selectedOptions={selectedTypes}
      onSelectionChange={onSelectionChange}
      getIconPath={getAttackTypeIconPath}
      columns={7}
    />
  )
}
