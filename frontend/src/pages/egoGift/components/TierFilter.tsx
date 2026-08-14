import { IconFilter } from '@/shared/filter'
import { EGO_GIFT_TIERS } from '@/shared/gameData'

import type { EGOGiftTier } from '@/shared/gameData'

interface TierFilterProps {
  selected: Set<EGOGiftTier>
  onSelectionChange: (tiers: Set<EGOGiftTier>) => void
}

/**
 * Tier text filter for filter sidebar
 * 6 text buttons (I, II, III, IV, V, EX) displayed in a flex row
 *
 * Reset is handled by parent "Reset All" button, not individual filters.
 *
 * Pattern: Wraps IconFilter in text mode (no getIconPath)
 */
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
