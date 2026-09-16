import { createFilterStore } from '@/components/hooks/filterStore'
import type { DungeonIdx, ThemePackFloor } from '@/shared/gameData'

/** The theme pack browser's filters, alive for the session rather than for one mount. */
export const themePackFilterStore = createFilterStore({
  selectedDifficulties: new Set<DungeonIdx>(),
  selectedFloors: new Set<ThemePackFloor>(),
  selectedEgoGifts: new Set<string>(),
})
