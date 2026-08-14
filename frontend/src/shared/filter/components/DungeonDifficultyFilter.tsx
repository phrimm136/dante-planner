import { IconFilter } from './IconFilter'
import { THEME_PACK_DIFFICULTIES, THEME_PACK_DIFFICULTY_LABELS } from '@/shared/gameData'

import type { DungeonIdx } from '@/shared/gameData'

interface DungeonDifficultyFilterProps {
  selected: Set<DungeonIdx>
  onSelectionChange: (difficulties: Set<DungeonIdx>) => void
}

/**
 * Dungeon difficulty filter for theme pack filtering.
 * 4 text buttons: Normal / Hard / Infinity / Extreme
 */
export function DungeonDifficultyFilter({
  selected,
  onSelectionChange,
}: DungeonDifficultyFilterProps) {
  return (
    <IconFilter
      options={THEME_PACK_DIFFICULTIES.map(String)}
      selectedOptions={new Set(Array.from(selected).map(String))}
      onSelectionChange={(options) => {
        onSelectionChange(new Set(Array.from(options).map(Number) as DungeonIdx[]))
      }}
      getLabel={(option) => THEME_PACK_DIFFICULTY_LABELS[Number(option) as DungeonIdx]}
    />
  )
}
