import { describe, expect, it } from 'vitest'
import { FLOOR_RULE_NAMES, FLOOR_RULE_TABLE } from '@/shared/gameData'
import { CHECKED_FLOOR_RULES } from '../floorRules'

describe('floor rule names', () => {
  it('checks exactly the rules the table names', () => {
    const checked = [...CHECKED_FLOOR_RULES].sort()
    expect(checked).toEqual([...FLOOR_RULE_NAMES].sort())
    expect(checked).toEqual(Object.keys(FLOOR_RULE_TABLE.rules).sort())
  })
})
