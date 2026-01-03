import { CompactIconFilter } from '@/components/filter/CompactIconFilter'
import { EGO_TYPES } from '@/lib/constants'
import { getEGOTypeIconPath } from '@/lib/assetPaths'

interface CompactEGOTypeFilterProps {
  selectedEGOTypes: Set<string>
  onSelectionChange: (types: Set<string>) => void
}

/**
 * Compact EGO type icon filter for filter sidebar
 * 5 EGO type icons displayed in a 7-column grid (matches keyword/skill attribute filters)
 * Icons stay small and left-aligned, not stretching to fill container width
 *
 * Pattern: Wraps CompactIconFilter like AttackTypeFilter wraps IconFilter
 * Uses columns={7} for consistency with CompactKeywordFilter and CompactSkillAttributeFilter
 */
export function CompactEGOTypeFilter({
  selectedEGOTypes,
  onSelectionChange,
}: CompactEGOTypeFilterProps) {
  return (
    <CompactIconFilter
      options={EGO_TYPES}
      selectedOptions={selectedEGOTypes}
      onSelectionChange={onSelectionChange}
      getIconPath={getEGOTypeIconPath}
      columns={7}
    />
  )
}

