import { describe, it, expect } from 'vitest'

import { contentPath, field, key } from '../../contentPath'
import { sinnerSkillViolations } from '../skillEAState'
import { rendered } from '../../__tests__/rendered'

const SINNER_PATH = contentPath(field('skillEAState'), key('01'))

const violationsOf = (slots: Record<string, unknown>) =>
  rendered(sinnerSkillViolations(slots, SINNER_PATH))

describe('sinnerSkillViolations', () => {
  it('passes three slots adding up to 6', () => {
    expect(violationsOf({ 0: 3, 1: 2, 2: 1 })).toEqual([])
  })

  it('reports a total other than 6 at the sinner with the total', () => {
    expect(violationsOf({ 0: 4, 1: 2, 2: 1 })).toEqual([
      { code: 'OUT_OF_RANGE_SKILL_TOTAL', path: 'skillEAState[01]', total: 7 },
    ])
  })

  it('reports a slot key outside 0-2 at the slot and leaves it out of the total', () => {
    expect(violationsOf({ 0: 3, 1: 2, 3: 1 })).toEqual([
      { code: 'OUT_OF_RANGE_SKILL_TOTAL', path: 'skillEAState[01]', total: 5 },
      { code: 'INVALID_SKILL_SLOT', path: 'skillEAState[01][3]' },
    ])
  })

  it('leaves a stored non-number out of the total instead of concatenating it', () => {
    expect(violationsOf({ 0: '3', 1: 2, 2: 1 })).toEqual([
      { code: 'OUT_OF_RANGE_SKILL_TOTAL', path: 'skillEAState[01]', total: 3 },
    ])
  })
})
