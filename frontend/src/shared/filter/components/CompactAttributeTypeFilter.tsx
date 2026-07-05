import { CompactIconFilter } from './CompactIconFilter'
import { EGO_GIFT_ATTRIBUTE_TYPES } from '@/shared/gameData'
import { getAffinityIconPath } from '@/shared/assets'

import type { EGOGiftAttributeType } from '@/shared/gameData'

interface CompactAttributeTypeFilterProps {
  selectedAttributeTypes: Set<EGOGiftAttributeType>
  onAttributeTypesChange: (types: Set<EGOGiftAttributeType>) => void
}

/**
 * Compact attribute type icon filter for EGO Gift filter sidebar
 * 7 affinity icons (CRIMSON, SCARLET, AMBER, SHAMROCK, AZURE, INDIGO, VIOLET)
 * displayed in a 7-column grid
 *
 * Pattern: Wraps CompactIconFilter like CompactEGOTypeFilter
 * Uses columns={7} for consistency with CompactKeywordFilter and CompactSkillAttributeFilter
 */
export function CompactAttributeTypeFilter({
  selectedAttributeTypes,
  onAttributeTypesChange,
}: CompactAttributeTypeFilterProps) {
  return (
    <CompactIconFilter
      options={EGO_GIFT_ATTRIBUTE_TYPES}
      selectedOptions={selectedAttributeTypes as Set<string>}
      onSelectionChange={(types) => {
        onAttributeTypesChange(types as Set<EGOGiftAttributeType>)
      }}
      getIconPath={getAffinityIconPath}
      columns={7}
    />
  )
}
