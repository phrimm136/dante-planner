import { createFilterStore } from '@/components/hooks/filterStore'
import type { AtkType, EgoType, Season, SkillAttributeType } from '@/shared/gameData'

/** The EGO browser's filters, alive for the session rather than for one mount. */
export const egoFilterStore = createFilterStore({
  selectedSinners: new Set<string>(),
  selectedKeywords: new Set<string>(),
  selectedBattleKeywords: new Set<string>(),
  selectedAttributes: new Set<SkillAttributeType>(),
  selectedAtkTypes: new Set<AtkType>(),
  selectedEGOTypes: new Set<EgoType>(),
  selectedSeasons: new Set<Season>(),
})
