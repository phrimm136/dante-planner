import { createFilterStore } from '@/components/hooks/filterStore'

export const abEventFilterStore = createFilterStore({
  selectedEgoGifts: new Set<string>(),
  selectedThemePacks: new Set<string>(),
})
