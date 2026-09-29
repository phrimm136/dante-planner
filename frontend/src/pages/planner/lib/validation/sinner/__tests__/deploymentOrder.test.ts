import { describe, it, expect } from 'vitest'

import { renderContentPath } from '../../contentPath'
import { deploymentOrderViolations } from '../deploymentOrder'

const violationsOf = (order: number[]) =>
  deploymentOrderViolations(order).map(({ code, path }) => ({
    code,
    path: renderContentPath(path),
  }))

describe('deploymentOrderViolations', () => {
  it('passes the order 0..11', () => {
    expect(violationsOf([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11])).toEqual([])
  })

  it('passes an empty order', () => {
    expect(violationsOf([])).toEqual([])
  })

  it('reports each index outside 0..11 at its position', () => {
    expect(violationsOf([-1, 3, 12, 99])).toEqual([
      { code: 'OUT_OF_RANGE_DEPLOYMENT', path: 'deploymentOrder[0]' },
      { code: 'OUT_OF_RANGE_DEPLOYMENT', path: 'deploymentOrder[2]' },
      { code: 'OUT_OF_RANGE_DEPLOYMENT', path: 'deploymentOrder[3]' },
    ])
  })
})

describe('deployment order repeats and length', () => {
  it('reports every repeat after the first occurrence', () => {
    expect(violationsOf([1, 1, 2, 1])).toEqual([
      { code: 'DUPLICATE_DEPLOYMENT', path: 'deploymentOrder[1]' },
      { code: 'DUPLICATE_DEPLOYMENT', path: 'deploymentOrder[3]' },
    ])
  })

  it('reports more than 12 entries at the list beside the per-entry checks', () => {
    expect(violationsOf([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 0])).toEqual([
      { code: 'EXCESS_DEPLOYMENT', path: 'deploymentOrder' },
      { code: 'DUPLICATE_DEPLOYMENT', path: 'deploymentOrder[12]' },
    ])
  })

  it('does not count an out-of-range entry as a repeat', () => {
    expect(violationsOf([12, 12])).toEqual([
      { code: 'OUT_OF_RANGE_DEPLOYMENT', path: 'deploymentOrder[0]' },
      { code: 'OUT_OF_RANGE_DEPLOYMENT', path: 'deploymentOrder[1]' },
    ])
  })
})
