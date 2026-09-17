import type { z } from 'zod'
import type { Entity } from '@/shared/filter'
import type { IdentityId } from '@/shared/gameData'
import type {
  IdentitySkillEntrySchema,
  IdentityDataSchema,
  IdentityI18nSchema,
  IdentitySpecSchema,
} from '../schemas/IdentitySchemas'

export type IdentitySpec = z.infer<typeof IdentitySpecSchema>

/** Identity entity for list/grid views: the spec entry plus its branded id. */
export type IdentityEntity = Entity<IdentityId, IdentitySpec>

export type { Uptie } from '@/shared/gameData'

export type IdentitySkillEntry = z.infer<typeof IdentitySkillEntrySchema>
export type IdentityData = z.infer<typeof IdentityDataSchema>
export type IdentityI18n = z.infer<typeof IdentityI18nSchema>
