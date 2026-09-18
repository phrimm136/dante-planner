import { createFilterStore } from '@/components/hooks/filterStore'
import type { DungeonIdx, ThemePackFloor } from '@/shared/gameData'

export const themePackFilterStore = createFilterStore({
  selectedDifficulties: new Set<DungeonIdx>(),
  selectedFloors: new Set<ThemePackFloor>(),
  selectedEgoGifts: new Set<string>(),
})
