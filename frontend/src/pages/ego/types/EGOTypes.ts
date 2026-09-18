import type { z } from 'zod'
import type { Entity } from '@/shared/filter'
import type { EGOId } from '@/shared/gameData'
import type {
  EGOSkillEntrySchema,
  EGODataSchema,
  EGOPassiveI18nSchema,
  EGOI18nSchema,
  EGOSpecSchema,
} from '../schemas/EGOSchemas'

export type { Threadspin } from '@/shared/gameData'

export type EgoSkillType = 'awaken' | 'erosion'

export type EGOSpec = z.infer<typeof EGOSpecSchema>

export type EGOEntity = Entity<EGOId, EGOSpec>

export type EGOSkillEntry = z.infer<typeof EGOSkillEntrySchema>
export type EGOData = z.infer<typeof EGODataSchema>
export type EGOPassiveI18n = z.infer<typeof EGOPassiveI18nSchema>
export type EGOI18n = z.infer<typeof EGOI18nSchema>
