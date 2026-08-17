import { describe, it, expect } from 'vitest'
import {
  BASE_BUFF_IDS,
  createBuffId,
  deriveEnhancements,
  getBaseIdFromBuffId,
  getEnhancementFromBuffId,
  getEnhancementSuffix,
  type EnhancementLevel,
} from '../StartBuffTypes'

const ENHANCEMENTS: EnhancementLevel[] = [0, 1, 2]

describe('buff id algebra', () => {
  it('round-trips every (baseId, enhancement) pair through createBuffId', () => {
    for (const baseId of BASE_BUFF_IDS) {
      for (const enhancement of ENHANCEMENTS) {
        const buffId = createBuffId(baseId, enhancement)

        expect(getBaseIdFromBuffId(buffId)).toBe(baseId)
        expect(getEnhancementFromBuffId(buffId)).toBe(enhancement)
      }
    }
  })

  it('encodes the enhancement as the hundreds digit', () => {
    expect(createBuffId(105, 0)).toBe(105)
    expect(createBuffId(105, 1)).toBe(205)
    expect(createBuffId(105, 2)).toBe(305)
  })

  it('maps enhancement levels to display suffixes', () => {
    expect(getEnhancementSuffix(0)).toBe('')
    expect(getEnhancementSuffix(1)).toBe('+')
    expect(getEnhancementSuffix(2)).toBe('++')
  })
})

describe('deriveEnhancements', () => {
  it('keys each selection by its base id', () => {
    expect(deriveEnhancements(new Set([201, 302]))).toEqual({ 101: 1, 102: 2 })
  })

  it('returns an empty record for an empty selection', () => {
    expect(deriveEnhancements(new Set())).toEqual({})
  })
})
