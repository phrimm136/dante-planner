import { CompactIconFilter } from '@/components/filter/CompactIconFilter'
import { getRarityIconPath } from '@/lib/assetPaths'

/** Rarity values as strings for CompactIconFilter compatibility */
const RANKS = ['1', '2', '3'] as const

interface CompactRarityFilterProps {
  selectedRaritys: Set<number>
  onSelectionChange: (ranks: Set<number>) => void
}

/**
 * Compact rank icon filter for filter sidebar
 * 3 rank icons displayed in a 7-column grid (matches attack type and other filters)
 * Icons are square, same size, and left-aligned
 *
 * Reset is handled by parent "Reset All" button, not individual filters.
 *
 * Pattern: Wraps CompactIconFilter like CompactAttackTypeFilter
 * Uses columns={7} for consistency with other compact filters
 */
export function CompactRarityFilter({
  selectedRaritys,
  onSelectionChange,
}: CompactRarityFilterProps) {
  // Convert Set<number> to Set<string> for CompactIconFilter
  // Using spread syntax which is optimized by React Compiler
  const selectedAsStrings = new Set([...selectedRaritys].map(String))

  // Convert Set<string> back to Set<number> for parent
  const handleSelectionChange = (strSet: Set<string>) => {
    onSelectionChange(new Set([...strSet].map(Number)))
  }

  return (
    <CompactIconFilter
      options={RANKS}
      selectedOptions={selectedAsStrings}
      onSelectionChange={handleSelectionChange}
      getIconPath={(rank: string) => getRarityIconPath(Number(rank))}
      flexIcons
    />
  )
}
