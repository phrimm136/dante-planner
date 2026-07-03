import { CompactIconFilter } from './CompactIconFilter'
import { THEME_PACK_FLOORS, THEME_PACK_FLOOR_LABELS } from '@/shared/gameData'

import type { ThemePackFloor } from '@/shared/gameData'

interface CompactFloorFilterProps {
  selectedFloors: Set<ThemePackFloor>
  onSelectionChange: (floors: Set<ThemePackFloor>) => void
}

/**
 * Compact floor filter for theme pack filtering.
 * 5 text buttons: 1F / 2F / 3F / 4F / 5F
 */
export function CompactFloorFilter({
  selectedFloors,
  onSelectionChange,
}: CompactFloorFilterProps) {
  return (
    <CompactIconFilter
      options={THEME_PACK_FLOORS.map(String)}
      selectedOptions={new Set(Array.from(selectedFloors).map(String))}
      onSelectionChange={(options) => {
        onSelectionChange(new Set(Array.from(options).map(Number) as ThemePackFloor[]))
      }}
      getLabel={(option) => THEME_PACK_FLOOR_LABELS[Number(option) as ThemePackFloor]}
      columns={5}
    />
  )
}
