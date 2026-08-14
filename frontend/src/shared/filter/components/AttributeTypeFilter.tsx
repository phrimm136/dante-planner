import { IconFilter } from './IconFilter'
import { EGO_GIFT_ATTRIBUTE_TYPES } from '@/shared/gameData'
import { getAffinityIconPath } from '@/shared/assets'

import type { EGOGiftAttributeType } from '@/shared/gameData'

interface AttributeTypeFilterProps {
  selected: Set<EGOGiftAttributeType>
  onSelectionChange: (types: Set<EGOGiftAttributeType>) => void
}

/**
 * Attribute type icon filter for EGO Gift filter sidebar
 * 7 affinity icons (CRIMSON, SCARLET, AMBER, SHAMROCK, AZURE, INDIGO, VIOLET)
 * displayed in a 7-column grid
 *
 * Pattern: Wraps IconFilter like EGOTypeFilter
 */
export function AttributeTypeFilter({
  selected,
  onSelectionChange,
}: AttributeTypeFilterProps) {
  return (
    <IconFilter
      options={EGO_GIFT_ATTRIBUTE_TYPES}
      selectedOptions={selected as Set<string>}
      onSelectionChange={(types) => {
        onSelectionChange(types as Set<EGOGiftAttributeType>)
      }}
      getIconPath={getAffinityIconPath}
    />
  )
}
