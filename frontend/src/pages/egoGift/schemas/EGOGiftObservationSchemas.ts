import { z } from 'zod'
import { EGOGiftIdSchema } from '@/shared/gameData'

export const EGOGiftObservationCostSchema = z
  .object({
    egogiftCount: z.number(),
    starlightCost: z.number(),
  })
  .strict()

export const EGOGiftObservationDataSchema = z
  .object({
    observationEgoGiftCostDataList: z.array(EGOGiftObservationCostSchema),
    observationEgoGiftDataList: z.array(EGOGiftIdSchema),
  })
  .strict()
