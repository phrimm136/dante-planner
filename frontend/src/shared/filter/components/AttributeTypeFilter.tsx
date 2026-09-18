import { IconFilter } from './IconFilter'
import { EGO_GIFT_ATTRIBUTE_TYPES } from '@/shared/gameData'
import { getAffinityIconPath } from '@/shared/assets'

import type { EGOGiftAttributeType } from '@/shared/gameData'

interface AttributeTypeFilterProps {
  selected: Set<EGOGiftAttributeType>
  onSelectionChange: (types: Set<EGOGiftAttributeType>) => void
}

export function AttributeTypeFilter({ selected, onSelectionChange }: AttributeTypeFilterProps) {
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
