import { describe, it, expect } from 'vitest'
import {
  EGOGiftObservationCostSchema,
  EGOGiftObservationDataSchema,
} from '../EGOGiftObservationSchemas'

describe('EGOGiftObservationCostSchema', () => {
  const validCost = { egogiftCount: 1, starlightCost: 70 }

  it('accepts a valid cost entry', () => {
    expect(EGOGiftObservationCostSchema.safeParse(validCost).success).toBe(true)
  })

  it('rejects an unknown key', () => {
    const result = EGOGiftObservationCostSchema.strict().safeParse({
      ...validCost,
      unexpected: true,
    })
    expect(result.success).toBe(false)
  })
})

describe('EGOGiftObservationDataSchema', () => {
  const validData = {
    observationEgoGiftCostDataList: [
      { egogiftCount: 1, starlightCost: 70 },
      { egogiftCount: 2, starlightCost: 160 },
    ],
    observationEgoGiftDataList: [9001, 9002, 9003],
  }

  it('accepts a valid observation data sample from egoGiftObservationData.json', () => {
    expect(EGOGiftObservationDataSchema.safeParse(validData).success).toBe(true)
  })

  it('rejects an unknown key', () => {
    const result = EGOGiftObservationDataSchema.strict().safeParse({
      ...validData,
      unexpected: true,
    })
    expect(result.success).toBe(false)
  })
})
