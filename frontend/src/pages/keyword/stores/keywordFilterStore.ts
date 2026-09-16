import { createFilterStore } from '@/components/hooks/filterStore'
import type { BuffType } from '@/shared/gameData'

/** The keyword browser's filters, alive for the session rather than for one mount. */
export const keywordFilterStore = createFilterStore({
  selectedBuffTypes: new Set<BuffType>(),
  selectedIdentities: new Set<string>(),
  selectedEgos: new Set<string>(),
  selectedEgoGifts: new Set<string>(),
})
