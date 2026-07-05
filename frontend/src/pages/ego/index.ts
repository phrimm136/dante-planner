// Public API of the ego entity. Import from '@/pages/ego', not internal paths.

export { EGOList } from './components/EGOList'
export { EGOCard } from './components/EGOCard'
export { EGOHeader } from './components/EGOHeader'
export { EGOHeaderWithI18n } from './components/EGOHeaderI18n'
export { SinCostPanel } from './components/SinCostPanel'
export { SinResistancePanel } from './components/SinResistancePanel'
export { SkillsSectionI18n } from './components/EGOSkillI18n'
export { PassiveCardWithSuspense } from './components/PassiveI18n'

export { useEGOListData, useEGOListSpec, useEGOListI18n } from './hooks/useEGOListData'
export { useEGODetailData, useEGODetailSpec, useEGODetailI18n } from './hooks/useEGODetailData'

export type {
  EGOType,
  EGOListItem,
  Threadspin,
  EGOSkillEntry,
  EGOSkillDataEntry,
  EGOPassiveI18n,
} from './types/EGOTypes'

export {
  EgoTypeSchema,
  EGODataSchema,
  EGOI18nSchema,
  EGOSpecListSchema,
  EGONameListSchema,
} from './schemas/EGOSchemas'
