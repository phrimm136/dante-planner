import { describe, it, expect } from 'vitest'
import { applyFacets } from '@/shared/filter'
import { BattleKeywordSpecListSchema } from '@/shared/gameText'
import specFixture from '../../__tests__/__fixtures__/battleKeywordSpecList.json'
import { KEYWORD_FACETS, type KeywordFacetState } from '../keywordFilter'
import { toKeywordEntity } from '../keywordEntity'

const KEYWORDS = Object.entries(BattleKeywordSpecListSchema.parse(specFixture)).map(([id, entry]) =>
  toKeywordEntity(id, entry),
)

const EMPTY_STATE: KeywordFacetState = {
  selectedBuffTypes: new Set(),
  selectedIdentities: new Set(),
  selectedEgos: new Set(),
  selectedEgoGifts: new Set(),
  selectedEnemies: new Set(),
  selectedAssistants: new Set(),
  selectedSinnerEvents: new Set(),
}

function survivors(state: Partial<KeywordFacetState>): string[] {
  const full = { ...EMPTY_STATE, ...state }
  return KEYWORDS.filter((keyword) => applyFacets(keyword, full, KEYWORD_FACETS))
    .map((keyword) => keyword.id)
    .sort()
}

describe('keyword battle card facets', () => {
  it('keeps exactly the keywords enemy card 8041 carries', () => {
    expect(survivors({ selectedEnemies: new Set(['8041']) })).toEqual([
      'Agility',
      'Bull_BuzzingEmotion',
      'Bull_Sadness',
      'Combustion',
      'Enhancement',
      'Sinking',
    ])
  })

  it('keeps the union of two enemy cards', () => {
    expect(survivors({ selectedEnemies: new Set(['8041', '8100']) })).toEqual([
      'AaCePcBa',
      'Agility',
      'Bull_BuzzingEmotion',
      'Bull_Sadness',
      'Combustion',
      'CrimsonTakeDamageUp',
      'Enhancement',
      'Sinking',
    ])
  })

  it('filters by assistant and sinner event cards', () => {
    expect(survivors({ selectedAssistants: new Set(['400013']) })).toEqual([
      'Agility',
      'Breath',
      'Burst',
      'Enhancement',
      'Laceration',
    ])
    expect(survivors({ selectedSinnerEvents: new Set(['400027']) })).toEqual([
      'Agility',
      'Enhancement',
      'Laceration',
    ])
  })
})
