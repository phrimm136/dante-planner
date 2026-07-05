import type { z } from 'zod'
import type { AtkType, Season, SkillAttributeType } from '@/shared/gameData'
import type {
  EgoTypeSchema,
  EGOSkillDataEntrySchema,
  EGOSkillDataTupleSchema,
  EGOSkillEntrySchema,
  EGOSkillsDataSchema,
  EGOPassiveListTupleSchema,
  EGOPassivesDataSchema,
  EGODataSchema,
  EGOSkillDescEntrySchema,
  EGOSkillI18nSchema,
  EGOPassiveI18nSchema,
  EGOI18nSchema,
} from '../schemas/EGOSchemas'

export type EGOType = z.infer<typeof EgoTypeSchema>

export type { Threadspin } from '@/shared/gameData'

/**
 * EGO list item for list/grid views.
 *
 * Assembled in EGOPage from already-validated spec + name list — not a direct
 * boundary shape, so it stays a plain TS type (deliberately uses plural
 * `attributeTypes`/`atkTypes`, unlike EGOSpecListItemSchema's singular keys).
 * Name is optional - populated when i18n is loaded, but components should prefer
 * EGOName component for granular Suspense boundaries.
 */
export interface EGOListItem {
  id: string
  /** Optional - populated when i18n is loaded */
  name?: string
  egoType: EGOType
  skillKeywordList: string[]
  battleKeywordList: string[]
  attributeTypes: SkillAttributeType[]
  atkTypes: AtkType[]
  updateDate: number
  season: Season
  maxThreadspin: 4 | 5
}

export type EGOSkillDataEntry = z.infer<typeof EGOSkillDataEntrySchema>
export type EGOSkillDataTuple = z.infer<typeof EGOSkillDataTupleSchema>
export type EGOSkillEntry = z.infer<typeof EGOSkillEntrySchema>
export type EGOSkillsData = z.infer<typeof EGOSkillsDataSchema>
export type EGOPassiveListTuple = z.infer<typeof EGOPassiveListTupleSchema>
export type EGOPassivesData = z.infer<typeof EGOPassivesDataSchema>
export type EGOData = z.infer<typeof EGODataSchema>
export type EGOSkillDescEntry = z.infer<typeof EGOSkillDescEntrySchema>
export type EGOSkillI18n = z.infer<typeof EGOSkillI18nSchema>
export type EGOPassiveI18n = z.infer<typeof EGOPassiveI18nSchema>
export type EGOI18n = z.infer<typeof EGOI18nSchema>
