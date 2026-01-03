import { Button } from '@/components/ui/button'

/** Rank values for identity filtering */
const RANKS = [1, 2, 3] as const

interface RankFilterProps {
  selectedRanks: Set<number>
  onSelectionChange: (ranks: Set<number>) => void
}

/**
 * Rank toggle button filter for filtering identities by rank (1, 2, 3)
 * Uses Button components with toggle styling
 *
 * Pattern: Custom toggle buttons following IconFilter selection logic
 */
export function RankFilter({
  selectedRanks,
  onSelectionChange,
}: RankFilterProps) {
  const toggleRank = (rank: number) => {
    const newSelection = new Set(selectedRanks)
    if (newSelection.has(rank)) {
      newSelection.delete(rank)
    } else {
      newSelection.add(rank)
    }
    onSelectionChange(newSelection)
  }

  const clearAll = () => {
    onSelectionChange(new Set())
  }

  return (
    <div className="bg-card border border-border rounded-md p-2 h-14 flex items-center gap-2">
      {/* Clear All Button */}
      <Button
        variant="outline"
        size="icon-sm"
        onClick={clearAll}
        className="shrink-0"
        title="Clear rank filters"
      >
        <span className="text-xs">X</span>
      </Button>

      {/* Rank Buttons */}
      <div className="flex gap-2">
        {RANKS.map((rank) => {
          const isSelected = selectedRanks.has(rank)
          return (
            <button
              key={rank}
              onClick={() => { toggleRank(rank); }}
              className="selectable shrink-0 w-8 h-8 rounded-md border-2 border-border bg-button flex items-center justify-center font-bold text-sm"
              data-selected={isSelected}
              title={`Rank ${rank}`}
            >
              {rank}
            </button>
          )
        })}
      </div>
    </div>
  )
}
