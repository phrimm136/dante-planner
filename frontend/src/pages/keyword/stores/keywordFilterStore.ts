import { createFilterStore } from '@/components/hooks/filterStore'
import type { BuffType } from '@/shared/gameData'

export const keywordFilterStore = createFilterStore({
  selectedBuffTypes: new Set<BuffType>(),
  selectedIdentities: new Set<string>(),
  selectedEgos: new Set<string>(),
  selectedEgoGifts: new Set<string>(),
  selectedEnemies: new Set<string>(),
  selectedAssistants: new Set<string>(),
  selectedSinnerEvents: new Set<string>(),
})
