import { describe, it, expect } from 'vitest'

import { contentPath, field } from '../../contentPath'
import { giftIdViolations } from '../giftIds'
import type { DuplicateGiftCode, GiftReferenceData } from '../giftIds'
import { rendered } from '../../__tests__/rendered'

const GIFTS = contentPath(field('observationGiftIds'))

const knowsAll: GiftReferenceData = { hasEgoGift: () => true }
const knows = (ids: string[]): GiftReferenceData => ({ hasEgoGift: (id) => ids.includes(id) })

const violationsOf = (
  giftIds: string[],
  data = knowsAll,
  duplicateCode: DuplicateGiftCode = 'DUPLICATE_EGOGIFT',
) => rendered(giftIdViolations(giftIds, GIFTS, duplicateCode, data))

describe('giftIdViolations', () => {
  it('passes unique ids', () => {
    expect(violationsOf(['9001', '9002', '9003'])).toEqual([])
  })

  it('reports each repeat with the given code at the repeat', () => {
    expect(violationsOf(['9001', '9002', '9001'])).toEqual([
      { code: 'DUPLICATE_EGOGIFT', path: 'observationGiftIds[2]' },
    ])
    expect(violationsOf(['9001', '9001'], knowsAll, 'DUPLICATE_STARTGIFT')).toEqual([
      { code: 'DUPLICATE_STARTGIFT', path: 'observationGiftIds[1]' },
    ])
  })

  it('reports an unknown id at its first occurrence and the repeat as a duplicate', () => {
    expect(violationsOf(['9999', '9999'], knows([]))).toEqual([
      { code: 'UNKNOWN_EGOGIFT', path: 'observationGiftIds[0]', giftId: '9999' },
      { code: 'DUPLICATE_EGOGIFT', path: 'observationGiftIds[1]' },
    ])
  })
})
