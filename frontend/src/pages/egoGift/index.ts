// Public API of the egoGift entity. Import from '@/pages/egoGift', not internal paths.

export { EGOGiftCard } from './components/EGOGiftCard'
export { EGOGiftCardLink } from './components/EGOGiftCardLink'
export { EGOGiftGrid } from './components/EGOGiftGrid'
export { EGOGiftList } from './components/EGOGiftList'
export { EGOGiftName } from './components/EGOGiftName'
export { EGOGiftMetadata } from './components/EGOGiftMetadata'
export { EGOGiftTooltip } from './components/EGOGiftTooltip'
export { EGOGiftFilterBar } from './components/EGOGiftFilterBar'
export { EGOGiftFilterDropdown } from './components/EGOGiftFilterDropdown'
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
export { calculateEnhancementCost, isMixedRecipe } from './lib/egoGiftUtils'
export { parseTier, toRomanTier } from './lib/egoGiftTier'
export type { EGOGiftTierValue } from './lib/egoGiftTier'
export { toEGOGiftCardProps } from './lib/egoGiftCardProps'
export { deriveDifficulty } from './lib/egoGiftFilter'
export type { EGOGiftFacetState } from './lib/egoGiftFilter'

export type {
  StandardRecipe,
  MixedRecipe,
  EGOGiftRecipe,
  EGOGiftSpec,
  EGOGiftData,
  EGOGiftI18n,
  EGOGiftListItem,
  EGOGiftNameList,
} from './types/EGOGiftTypes'
export {
  EGOGiftSpecSchema,
  EGOGiftDataSchema,
  EGOGiftI18nSchema,
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
  findEncodedGiftId,
  buildSelectionLookup,
  getCascadeIngredients,
  decodeGiftSelections,
  sortGiftSelections,
  lookupByGiftId,
  hasGiftId,
  giftDisplayName,
} from './lib/egoGiftEncoding'
export type { GiftSelectionEntry, DecodedGiftSelection } from './lib/egoGiftEncoding'
