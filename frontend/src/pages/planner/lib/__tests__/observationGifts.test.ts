import { describe, it, expect } from 'vitest'
import md7Observation from '@static/data/MD7/egoGiftObservationData.json'
import { maxObservableGifts } from '../observationGifts'

describe('maxObservableGifts', () => {
  it('allows as many gifts as the largest priced count', () => {
    const costs = [1, 2, 3, 4].map((egogiftCount) => ({ egogiftCount, starlightCost: 70 }))

    expect(maxObservableGifts(costs)).toBe(4)
  })

  it('allows 3 gifts in MD7', () => {
    expect(maxObservableGifts(md7Observation.observationEgoGiftCostDataList)).toBe(3)
  })
})
