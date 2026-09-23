import type { z } from 'zod'
import type { Entity } from '@/shared/filter'
import type { EGOGiftId } from '@/shared/gameData'
import type {
  StandardRecipeSchema,
  MixedRecipeSchema,
  EGOGiftRecipeSchema,
  EGOGiftSpecSchema,
  EGOGiftDataSchema,
  EGOGiftI18nSchema,
  EGOGiftNameListSchema,
} from '../schemas/EGOGiftSchemas'

export type StandardRecipe = z.infer<typeof StandardRecipeSchema>
export type MixedRecipe = z.infer<typeof MixedRecipeSchema>
export type EGOGiftRecipe = z.infer<typeof EGOGiftRecipeSchema>

export type EGOGiftSpec = z.infer<typeof EGOGiftSpecSchema>
export type EGOGiftData = z.infer<typeof EGOGiftDataSchema>
export type EGOGiftI18n = z.infer<typeof EGOGiftI18nSchema>

export type EGOGiftEntity = Entity<EGOGiftId, EGOGiftSpec>

export type EGOGiftNameList = z.infer<typeof EGOGiftNameListSchema>
