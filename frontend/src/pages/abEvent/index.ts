// Public API of the abEvent entity. Import from '@/pages/abEvent', not internal paths.

export { AbEventList } from './components/AbEventList'
export { AbEventCard } from './components/AbEventCard'
export { ChoiceBranch } from './components/AbEventChoiceBranch'

export { useAbEventListData, useAbEventListSpec } from './hooks/useAbEventListData'
export {
  useAbEventDetailData,
  useAbEventDetailSpec,
  useAbEventDetailI18n,
  useAbEventShared,
} from './hooks/useAbEventDetailData'

export { createEffectTextResolver } from './lib/abEventTextResolver'

export {
  AbEventSpecListEntrySchema,
  AbEventSpecListSchema,
  AbEventDataSchema,
  AbEventI18nSchema,
  AbEventSharedSchema,
} from './schemas/AbEventSchemas'

export type { CoinTossI18nContext } from './lib/abEventTextResolver'
export type { AbEventChoice } from './types/AbEventTypes'
