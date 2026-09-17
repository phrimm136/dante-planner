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

/** Skill families an EGO can have; erosion also selects the corrosion CG. */
export type EgoSkillType = 'awaken' | 'erosion'

export type EGOSpec = z.infer<typeof EGOSpecSchema>

/**
 * EGO entity for list/grid views.
 *
 * Assembled in list components from already-validated spec + name list — not a
 * direct boundary shape, so it stays a plain TS type. Components should prefer
 * EGOName for granular Suspense boundaries.
 */
export type EGOEntity = Entity<EGOId, EGOSpec>

export type EGOSkillEntry = z.infer<typeof EGOSkillEntrySchema>
export type EGOData = z.infer<typeof EGODataSchema>
export type EGOPassiveI18n = z.infer<typeof EGOPassiveI18nSchema>
export type EGOI18n = z.infer<typeof EGOI18nSchema>
