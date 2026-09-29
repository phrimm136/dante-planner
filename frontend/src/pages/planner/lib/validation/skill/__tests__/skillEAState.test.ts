import { describe, it, expect } from 'vitest'

import { contentPath, field, key } from '../../contentPath'
import { sinnerSkillViolations } from '../skillEAState'
import { rendered } from '../../__tests__/rendered'

const SINNER_PATH = contentPath(field('skillEAState'), key('1'))

const violationsOf = (slots: { 0: number; 1: number; 2: number }) =>
  rendered(sinnerSkillViolations(slots, SINNER_PATH))

describe('sinnerSkillViolations', () => {
  it('passes three slots adding up to 6', () => {
    expect(violationsOf({ 0: 3, 1: 2, 2: 1 })).toEqual([])
  })

  it('reports a total other than 6 at the sinner with the total', () => {
    expect(violationsOf({ 0: 4, 1: 2, 2: 1 })).toEqual([
      { code: 'OUT_OF_RANGE_SKILL_TOTAL', path: 'skillEAState[1]', total: 7 },
    ])
  })
})
