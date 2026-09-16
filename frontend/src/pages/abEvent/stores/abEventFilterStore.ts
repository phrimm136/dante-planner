import { createFilterStore } from '@/components/hooks/filterStore'

/** The abnormality event browser's filters, alive for the session rather than for one mount. */
export const abEventFilterStore = createFilterStore({
  selectedEgoGifts: new Set<string>(),
  selectedThemePacks: new Set<string>(),
})
