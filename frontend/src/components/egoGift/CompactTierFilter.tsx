import { CompactIconFilter } from '@/components/filter/CompactIconFilter'
import { EGO_GIFT_TIERS } from '@/lib/constants'

import type { EGOGiftTier } from '@/lib/constants'

interface CompactTierFilterProps {
  selectedTiers: Set<EGOGiftTier>
  onSelectionChange: (tiers: Set<EGOGiftTier>) => void
}

/**
 * Compact tier text filter for filter sidebar
 * 6 text buttons (I, II, III, IV, V, EX) displayed in a flex row
 *
 * Reset is handled by parent "Reset All" button, not individual filters.
 *
 * Pattern: Wraps CompactIconFilter in text mode (no getIconPath)
 */
export function CompactTierFilter({
  selectedTiers,
  onSelectionChange,
}: CompactTierFilterProps) {
  return (
    <CompactIconFilter
      options={EGO_GIFT_TIERS}
      selectedOptions={selectedTiers as Set<string>}
      onSelectionChange={(options) => {
        onSelectionChange(options as Set<EGOGiftTier>)
      }}
      getLabel={(tier) => tier}
      columns={6}
    />
  )
}
