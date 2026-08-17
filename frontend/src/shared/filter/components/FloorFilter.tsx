import { IconFilter } from './IconFilter'
import { THEME_PACK_FLOORS, THEME_PACK_FLOOR_LABELS } from '@/shared/gameData'

import type { ThemePackFloor } from '@/shared/gameData'

interface FloorFilterProps {
  selected: Set<ThemePackFloor>
  onSelectionChange: (floors: Set<ThemePackFloor>) => void
}

/**
 * Floor filter for theme pack filtering.
 * 5 text buttons: 1F / 2F / 3F / 4F / 5F
 */
export function FloorFilter({ selected, onSelectionChange }: FloorFilterProps) {
  return (
    <IconFilter
      options={THEME_PACK_FLOORS.map(String)}
      selectedOptions={new Set(Array.from(selected).map(String))}
      onSelectionChange={(options) => {
        onSelectionChange(new Set(Array.from(options).map(Number) as ThemePackFloor[]))
      }}
      getLabel={(option) => THEME_PACK_FLOOR_LABELS[Number(option) as ThemePackFloor]}
    />
  )
}
