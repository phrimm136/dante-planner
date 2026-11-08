import { createFilterStore } from '@/components/hooks/filterStore'
import type { AbEventResultType } from '@/shared/gameData'

export const abEventFilterStore = createFilterStore({
  selectedEgoGifts: new Set<string>(),
  selectedThemePacks: new Set<string>(),
  selectedResultTypes: new Set<AbEventResultType>(),
})
