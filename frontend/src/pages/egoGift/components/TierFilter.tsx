import { IconFilter } from '@/shared/filter'
import { EGO_GIFT_TIERS } from '@/shared/gameData'

import type { EGOGiftTier } from '@/shared/gameData'

interface TierFilterProps {
  selected: Set<EGOGiftTier>
  onSelectionChange: (tiers: Set<EGOGiftTier>) => void
}

export function TierFilter({ selected, onSelectionChange }: TierFilterProps) {
  return (
    <IconFilter
      options={EGO_GIFT_TIERS}
      selectedOptions={selected as Set<string>}
      onSelectionChange={(options) => {
        onSelectionChange(options as Set<EGOGiftTier>)
      }}
      getLabel={(tier) => tier}
    />
  )
}
