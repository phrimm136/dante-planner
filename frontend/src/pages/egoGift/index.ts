export { EGOGiftCardLink } from './components/EGOGiftCardLink'
export { EGOGiftDetailSkeleton } from './components/EGOGiftDetailSkeleton'
export { EGOGiftList } from './components/EGOGiftList'
export { EGOGiftMetadata } from './components/EGOGiftMetadata'
export { EGOGiftTooltip } from './components/EGOGiftTooltip'
export { EGOGiftFilterBar } from './components/EGOGiftFilterBar'
export { EGOGiftSelectionList } from './components/EGOGiftSelectionList'
export { EGOGiftSelectableCard } from './components/EGOGiftSelectableCard'
export { EGOGiftObservationSelection } from './components/EGOGiftObservationSelection'
export { RecipeSection } from './components/RecipeSection'
export { GiftNameI18n } from './components/GiftNameI18n'
export { EnhancementsPanelI18n } from './components/EnhancementsPanelI18n'
export { EGOGiftKeywordFilter } from './components/EGOGiftKeywordFilter'
export { DifficultyFilter } from './components/DifficultyFilter'
export { TierFilter } from './components/TierFilter'

export {
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
export { deriveDifficulty } from './lib/egoGiftFilter'
export type { EGOGiftFacetState } from './lib/egoGiftFilter'

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
  orderSelectionsByGiftOrder,
  decodeAndOrderGiftSelections,
  lookupByGiftId,
  hasGiftId,
  giftDisplayName,
} from './lib/egoGiftEncoding'
export type { GiftSelectionEntry, DecodedGiftSelection } from './lib/egoGiftEncoding'
