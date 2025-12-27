/**
 * EGO Gift Observation Types
 *
 * TypeScript types for EGO gift observation data structures.
 * Used for MD6 gift observation feature.
 */

/**
 * Cost data entry mapping gift count to starlight cost
 */
export interface EGOGiftObservationCost {
  egogiftCount: number
  starlightCost: number
}

/**
 * Complete observation data including costs and eligible gift IDs
 */
export interface EGOGiftObservationData {
  observationEgoGiftCostDataList: EGOGiftObservationCost[]
  observationEgoGiftDataList: number[]
}
