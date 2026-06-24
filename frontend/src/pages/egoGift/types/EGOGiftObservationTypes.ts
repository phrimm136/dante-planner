import type { z } from 'zod'
import type {
  EGOGiftObservationCostSchema,
  EGOGiftObservationDataSchema,
} from '../schemas/EGOGiftObservationSchemas'

/**
 * Cost data entry mapping gift count to starlight cost
 */
export type EGOGiftObservationCost = z.infer<typeof EGOGiftObservationCostSchema>

/**
 * Complete observation data including costs and eligible gift IDs.
 * Used for MD6 gift observation feature.
 */
export type EGOGiftObservationData = z.infer<typeof EGOGiftObservationDataSchema>
