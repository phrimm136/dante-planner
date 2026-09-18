import { createFilterStore } from '@/components/hooks/filterStore'
import type { EGOGiftAttributeType, EGOGiftDifficulty, EGOGiftTier } from '@/shared/gameData'

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
