import { CompactIconFilter } from './CompactIconFilter'
import { THEME_PACK_DIFFICULTIES, THEME_PACK_DIFFICULTY_LABELS } from '@/shared/gameData'

import type { DungeonIdx } from '@/shared/gameData'

interface CompactDungeonDifficultyFilterProps {
  selectedDifficulties: Set<DungeonIdx>
  onSelectionChange: (difficulties: Set<DungeonIdx>) => void
}

/**
 * Compact dungeon difficulty filter for theme pack filtering.
 * 4 text buttons: Normal / Hard / Infinity / Extreme
 */
export function CompactDungeonDifficultyFilter({
  selectedDifficulties,
  onSelectionChange,
}: CompactDungeonDifficultyFilterProps) {
  return (
    <CompactIconFilter
      options={THEME_PACK_DIFFICULTIES.map(String)}
      selectedOptions={new Set(Array.from(selectedDifficulties).map(String))}
      onSelectionChange={(options) => {
        onSelectionChange(new Set(Array.from(options).map(Number) as DungeonIdx[]))
      }}
      getLabel={(option) => THEME_PACK_DIFFICULTY_LABELS[Number(option) as DungeonIdx]}
      columns={4}
    />
  )
}
