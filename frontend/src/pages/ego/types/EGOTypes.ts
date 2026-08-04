import type { z } from 'zod'
import type { AtkType, EgoType, Season, SkillAttributeType } from '@/shared/gameData'
import type {
  EGOSkillEntrySchema,
  EGODataSchema,
  EGOPassiveI18nSchema,
  EGOI18nSchema,
} from '../schemas/EGOSchemas'

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
  egoType: EgoType
  skillKeywordList: string[]
  battleKeywordList: string[]
  attributeTypes: SkillAttributeType[]
  atkTypes: AtkType[]
  updateDate: number
  season: Season
  maxThreadspin: 4 | 5
}

export type EGOSkillEntry = z.infer<typeof EGOSkillEntrySchema>
export type EGOData = z.infer<typeof EGODataSchema>
export type EGOPassiveI18n = z.infer<typeof EGOPassiveI18nSchema>
export type EGOI18n = z.infer<typeof EGOI18nSchema>
