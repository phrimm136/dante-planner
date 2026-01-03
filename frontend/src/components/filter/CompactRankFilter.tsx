import { CompactIconFilter } from '@/components/filter/CompactIconFilter'
import { getRankIconPath } from '@/lib/assetPaths'

/** Rank values as strings for CompactIconFilter compatibility */
const RANKS = ['1', '2', '3'] as const

interface CompactRankFilterProps {
  selectedRanks: Set<number>
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
export function CompactRankFilter({
  selectedRanks,
  onSelectionChange,
}: CompactRankFilterProps) {
  // Convert Set<number> to Set<string> for CompactIconFilter
  // Using spread syntax which is optimized by React Compiler
  const selectedAsStrings = new Set([...selectedRanks].map(String))

  // Convert Set<string> back to Set<number> for parent
  const handleSelectionChange = (strSet: Set<string>) => {
    onSelectionChange(new Set([...strSet].map(Number)))
  }

  return (
    <CompactIconFilter
      options={RANKS}
      selectedOptions={selectedAsStrings}
      onSelectionChange={handleSelectionChange}
      getIconPath={(rank: string) => getRankIconPath(Number(rank))}
      flexIcons
    />
  )
}
