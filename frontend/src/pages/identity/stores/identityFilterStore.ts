import { createFilterStore } from '@/components/hooks/filterStore'
import type { AtkType, DefType, Season, SkillAttributeType } from '@/shared/gameData'

/** The identity browser's filters, alive for the session rather than for one mount. */
export const identityFilterStore = createFilterStore({
  selectedSinners: new Set<string>(),
  selectedKeywords: new Set<string>(),
  selectedBattleKeywords: new Set<string>(),
  selectedAttributes: new Set<SkillAttributeType>(),
  selectedAtkTypes: new Set<AtkType>(),
  selectedDefTypes: new Set<DefType>(),
  selectedRaritys: new Set<number>(),
  selectedSeasons: new Set<Season>(),
  selectedUnitKeywords: new Set<string>(),
})
