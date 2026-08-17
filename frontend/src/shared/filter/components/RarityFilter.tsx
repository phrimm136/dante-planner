import { IconFilter } from './IconFilter'
import { getRarityIconPath } from '@/shared/assets'

/** Rarity values as strings for IconFilter compatibility */
const RANKS = ['1', '2', '3'] as const

interface RarityFilterProps {
  selected: Set<number>
  onSelectionChange: (ranks: Set<number>) => void
}

/**
 * Rank icon filter for filter sidebar
 * 3 rank icons displayed in a 7-column grid (matches attack type and other filters)
 * Icons are square, same size, and left-aligned
 *
 * Reset is handled by parent "Reset All" button, not individual filters.
 *
 */
export function RarityFilter({ selected, onSelectionChange }: RarityFilterProps) {
  // Convert Set<number> to Set<string> for IconFilter
  // Using spread syntax which is optimized by React Compiler
  const selectedAsStrings = new Set([...selected].map(String))

  // Convert Set<string> back to Set<number> for parent
  const handleSelectionChange = (strSet: Set<string>) => {
    onSelectionChange(new Set([...strSet].map(Number)))
  }

  return (
    <IconFilter
      options={RANKS}
      selectedOptions={selectedAsStrings}
      onSelectionChange={handleSelectionChange}
      getIconPath={(rank: string) => getRarityIconPath(Number(rank))}
      flexIcons
    />
  )
}
