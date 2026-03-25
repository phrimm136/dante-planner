import { describe, it, expect } from 'vitest'
import { getDefenseTypeIconPath } from '../assetPaths'
import type { DefType } from '../constants'
import { DEF_TYPES } from '../constants'

describe('getDefenseTypeIconPath', () => {
  it('returns a valid hashed path for each defense type', () => {
    for (const defType of DEF_TYPES) {
      const path = getDefenseTypeIconPath(defType)
      expect(path).toMatch(/^\/a\/[a-f0-9]+\.webp$/)
    }
  })

  it('returns unique paths for each defense type', () => {
    const paths = DEF_TYPES.map(getDefenseTypeIconPath)
    expect(new Set(paths).size).toBe(DEF_TYPES.length)
  })
})

describe('DEF_TYPES constant', () => {
  it('has exactly 5 types', () => {
    expect(DEF_TYPES).toHaveLength(5)
  })

  it('contains all expected values', () => {
    expect(DEF_TYPES).toContain('EVADE')
    expect(DEF_TYPES).toContain('COUNTER')
    expect(DEF_TYPES).toContain('CLASHABLE_COUNTER')
    expect(DEF_TYPES).toContain('GUARD')
    expect(DEF_TYPES).toContain('CLASHABLE_GUARD')
  })

  it('maintains expected order', () => {
    expect([...DEF_TYPES]).toEqual([
      'GUARD', 'EVADE', 'COUNTER', 'CLASHABLE_GUARD', 'CLASHABLE_COUNTER',
    ])
  })
})

describe('defense type filter matching (AND logic)', () => {
  function matchesDefenseTypeFilter(defenseTypes: DefType[], selected: Set<DefType>): boolean {
    if (selected.size === 0) {
      return true
    }
    return Array.from(selected).every(dt => defenseTypes.includes(dt))
  }

  it('empty selection matches all', () => {
    expect(matchesDefenseTypeFilter(['GUARD'], new Set())).toBe(true)
  })

  it('single type selected, identity has it', () => {
    expect(matchesDefenseTypeFilter(['GUARD'], new Set(['GUARD'] as DefType[]))).toBe(true)
  })

  it('single type selected, identity does not have it', () => {
    expect(matchesDefenseTypeFilter(['EVADE'], new Set(['GUARD'] as DefType[]))).toBe(false)
  })

  it('multiple types selected, identity has all of them (AND)', () => {
    expect(matchesDefenseTypeFilter(
      ['EVADE', 'COUNTER'],
      new Set(['EVADE', 'COUNTER'] as DefType[])
    )).toBe(true)
  })

  it('multiple types selected, identity has only one (AND fails)', () => {
    expect(matchesDefenseTypeFilter(
      ['EVADE'],
      new Set(['EVADE', 'COUNTER'] as DefType[])
    )).toBe(false)
  })

  it('identity with multiple defense types, all selected match', () => {
    expect(matchesDefenseTypeFilter(
      ['GUARD', 'CLASHABLE_GUARD'],
      new Set(['GUARD'] as DefType[])
    )).toBe(true)
  })

  it('identity with multiple defense types, none match', () => {
    expect(matchesDefenseTypeFilter(
      ['GUARD', 'CLASHABLE_GUARD'],
      new Set(['EVADE'] as DefType[])
    )).toBe(false)
  })
})
