import { createFilterStore } from '@/components/hooks/filterStore'
import type { EGOGiftAttributeType, EGOGiftDifficulty, EGOGiftTier } from '@/shared/gameData'

/** The EGO gift browser's filters, alive for the session rather than for one mount. */
export const egoGiftFilterStore = createFilterStore({
  selectedKeywords: new Set<string>(),
  selectedBattleKeywords: new Set<string>(),
  selectedDifficulties: new Set<EGOGiftDifficulty>(),
  selectedTiers: new Set<EGOGiftTier>(),
  selectedThemePacks: new Set<string>(),
  selectedAttributeTypes: new Set<EGOGiftAttributeType>(),
  selectedFusioned: new Set<string>(),
  selectedExclusive: new Set<string>(),
})
