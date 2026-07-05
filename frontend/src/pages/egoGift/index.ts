// Public API of the egoGift entity. Import from '@/pages/egoGift', not internal paths.

export { EGOGiftCard } from './components/EGOGiftCard'
export { EGOGiftCardLink } from './components/EGOGiftCardLink'
export { EGOGiftList } from './components/EGOGiftList'
export { EGOGiftName } from './components/EGOGiftName'
export { EGOGiftMetadata } from './components/EGOGiftMetadata'
export { EGOGiftTooltip } from './components/EGOGiftTooltip'
export { EGOGiftFilterBar } from './components/EGOGiftFilterBar'
export { EGOGiftSelectionList } from './components/EGOGiftSelectionList'
export { EGOGiftSelectableCard } from './components/EGOGiftSelectableCard'
export { EGOGiftObservationSelection } from './components/EGOGiftObservationSelection'
export { EGOGiftEnhancementIndicator } from './components/EGOGiftEnhancementIndicator'
export { RecipeSection } from './components/RecipeSection'
export { GiftNameI18n } from './components/GiftNameI18n'
export { EnhancementsPanelI18n } from './components/EnhancementsPanelI18n'
export { CompactEGOGiftKeywordFilter } from './components/CompactEGOGiftKeywordFilter'
export { CompactDifficultyFilter } from './components/CompactDifficultyFilter'
export { CompactTierFilter } from './components/CompactTierFilter'

export {
  useEGOGiftListData,
  useEGOGiftListSpec,
  useEGOGiftListI18n,
  useEGOGiftListI18nDeferred,
  egoGiftListQueryKeys,
} from './hooks/useEGOGiftListData'
export {
  useEGOGiftDetailData,
  useEGOGiftDetailSpec,
  useEGOGiftDetailI18n,
  egoGiftDetailQueryKeys,
} from './hooks/useEGOGiftDetailData'
export {
  useEGOGiftObservationData,
  egoGiftObservationQueryKeys,
} from './hooks/useEGOGiftObservationData'

export { sortEGOGifts } from './lib/egoGiftSort'
export {
  calculateEnhancementCost,
  extractEGOGiftTier,
  getDisabledEnhancementLevels,
  getMaxEnhancementLevel,
} from './lib/egoGiftUtils'
export {
  deriveDifficulty,
  matchesKeywordFilter,
  matchesDifficultyFilter,
  matchesTierFilter,
  matchesThemePackFilter,
  matchesAttributeTypeFilter,
  matchesFusionedFilter,
  matchesExclusiveFilter,
} from './lib/egoGiftFilter'

export type {
  StandardRecipe,
  MixedRecipe,
  EGOGiftRecipe,
  EGOGiftSpec,
  EGOGiftData,
  EGOGiftI18n,
  EGOGiftListItem,
  EGOGiftListItemWithName,
  EGOGiftSpecList,
  EGOGiftNameList,
} from './types/EGOGiftTypes'
export type {
  EGOGiftObservationCost,
  EGOGiftObservationData,
} from './types/EGOGiftObservationTypes'

export {
  EGOGiftSpecSchema,
  EGOGiftDataSchema,
  EGOGiftI18nSchema,
  EGOGiftListItemSchema,
  EGOGiftSpecListSchema,
  EGOGiftNameListSchema,
} from './schemas/EGOGiftSchemas'
export {
  EGOGiftObservationCostSchema,
  EGOGiftObservationDataSchema,
} from './schemas/EGOGiftObservationSchemas'

export {
  encodeGiftSelection,
  decodeGiftSelection,
  getBaseGiftId,
  isGiftSelected,
  getGiftEnhancement,
  findEncodedGiftId,
  buildSelectionLookup,
  getCascadeIngredients,
} from './lib/egoGiftEncoding'
export type { GiftSelectionEntry } from './lib/egoGiftEncoding'
