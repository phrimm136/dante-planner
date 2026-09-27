import { describe, it, expect } from 'vitest'
import plannerKeywordsI18n from '@static/i18n/EN/plannerKeywords.json'
import { KEYWORD_RENAME_MAP, PLANNER_KEYWORDS } from '../constants'

describe('planner keyword parity', () => {
  it('PLANNER_KEYWORDS holds exactly the keys of EN plannerKeywords.json', () => {
    expect([...PLANNER_KEYWORDS].sort()).toEqual(Object.keys(plannerKeywordsI18n).sort())
  })

  it('every KEYWORD_RENAME_MAP target is a planner keyword', () => {
    const plannerKeywords = new Set<string>(PLANNER_KEYWORDS)
    const strayTargets = Object.values(KEYWORD_RENAME_MAP).filter((id) => !plannerKeywords.has(id))
    expect(strayTargets).toEqual([])
  })
})
