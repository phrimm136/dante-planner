import { describe, it, expect } from 'vitest'

import { ALLOWED_FLOOR_DIFFICULTIES, FLOOR_COUNTS, MD_CATEGORIES } from '../constants'
import { allowedDifficulties, floorCount } from '../floorRules'

describe('floor rule table equals FLOOR_COUNTS and ALLOWED_FLOOR_DIFFICULTIES; leaves with those constants', () => {
  it.each(MD_CATEGORIES)('%s has the constant floor count', (category) => {
    expect(floorCount(category)).toBe(FLOOR_COUNTS[category])
  })

  it.each(MD_CATEGORIES)('%s allows the constant difficulties on every floor', (category) => {
    const fromTable = Array.from(
      { length: floorCount(category) },
      (_, floor) => new Set(allowedDifficulties(category, floor)),
    )
    const fromConstants = ALLOWED_FLOOR_DIFFICULTIES[category].map(
      (difficulties) => new Set(difficulties),
    )
    expect(fromTable).toEqual(fromConstants)
  })
})
