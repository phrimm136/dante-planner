import { z } from 'zod'

/**
 * EGO Gift Observation Schemas
 *
 * Zod schemas for runtime validation of EGO gift observation data.
 * Includes cost data (count -> starlight cost) and eligible gift ID list.
 */

// Cost data schema - maps gift count to starlight cost
export const EGOGiftObservationCostSchema = z.object({
  egogiftCount: z.number(),
  starlightCost: z.number(),
}).strict()

// Observation data schema - cost list + gift ID list
export const EGOGiftObservationDataSchema = z.object({
  observationEgoGiftCostDataList: z.array(EGOGiftObservationCostSchema),
  observationEgoGiftDataList: z.array(z.number()),
}).strict()
