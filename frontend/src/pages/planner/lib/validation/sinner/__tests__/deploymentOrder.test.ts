import { describe, it, expect } from 'vitest'

import { renderContentPath } from '../../contentPath'
import { deploymentOrderViolations } from '../deploymentOrder'

const violationsOf = (raw: unknown) =>
  deploymentOrderViolations(raw).map(({ code, path }) => ({
    code,
    path: renderContentPath(path),
  }))

describe('deploymentOrderViolations', () => {
  it('passes the order 0..11', () => {
    expect(violationsOf([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11])).toEqual([])
  })

  it('passes an empty order and an absent one', () => {
    expect(violationsOf([])).toEqual([])
    expect(violationsOf(undefined)).toEqual([])
  })

  it('reports each index outside 0..11 at its position', () => {
    expect(violationsOf([-1, 3, 12, 99])).toEqual([
      { code: 'OUT_OF_RANGE_DEPLOYMENT', path: 'deploymentOrder[0]' },
      { code: 'OUT_OF_RANGE_DEPLOYMENT', path: 'deploymentOrder[2]' },
      { code: 'OUT_OF_RANGE_DEPLOYMENT', path: 'deploymentOrder[3]' },
    ])
  })

  it.each([
    ['a string', '3'],
    ['a fraction', 1.5],
    ['null', null],
  ])('reports an entry that is %s as INVALID_FIELD_TYPE', (_label, entry) => {
    expect(violationsOf([0, entry])).toEqual([
      { code: 'INVALID_FIELD_TYPE', path: 'deploymentOrder[1]' },
    ])
  })

  it.each([
    ['null', null],
    ['a number', 3],
    ['an object', {}],
  ])('reports an order that is %s as INVALID_FIELD_TYPE', (_label, raw) => {
    expect(violationsOf(raw)).toEqual([{ code: 'INVALID_FIELD_TYPE', path: 'deploymentOrder' }])
  })
})
