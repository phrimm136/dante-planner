/**
 * keywordFormatter.test.ts
 *
 * Unit tests for keyword formatter pure functions.
 * Tests parsing [BracketedKeywords], type resolution, color lookup, and full resolution.
 */

import { describe, it, expect } from 'vitest'
import {
  parseKeywords,
  resolveKeywordType,
  getKeywordColor,
  resolveKeyword,
  formatDescription,
} from '../keywordFormatter'
import type { KeywordResolutionContext } from '@/types/KeywordTypes'

// Test fixtures
const mockBattleKeywords: KeywordResolutionContext['battleKeywords'] = {
  Sinking: {
    name: 'Sinking',
    desc: 'Each turn, lose HP equal to Sinking count.',
    iconId: 'Sinking',
    buffType: 'Negative',
  },
  Burn: {
    name: 'Burn',
    desc: 'At turn end, deal damage equal to Burn count.',
    iconId: null,
    buffType: 'Negative',
  },
  Protection: {
    name: 'Protection',
    desc: 'Gain defense.',
    iconId: 'Protection',
    buffType: 'Positive',
  },
}

const mockSkillTags: KeywordResolutionContext['skillTags'] = {
  WhenUse: '[On Use]',
  OnSucceedAttack: '[On Hit]',
  OnKill: '[On Kill]',
}

const mockColorCodes: KeywordResolutionContext['colorCodes'] = {
  Positive: '#00ff00',
  Negative: '#ff0000',
  Neutral: '#ffff00',
  WhenUse: '#0000ff',
  OnSucceedAttack: '#00ffff',
  Critical: '#93f13e',
}

const mockContext: KeywordResolutionContext = {
  battleKeywords: mockBattleKeywords,
  skillTags: mockSkillTags,
  colorCodes: mockColorCodes,
}

describe('parseKeywords', () => {
  describe('basic parsing', () => {
    it('returns empty array for empty string', () => {
      const result = parseKeywords('')
      expect(result).toEqual([])
    })

    it('returns single text segment for plain text without brackets', () => {
      const result = parseKeywords('Plain text without keywords')
      expect(result).toEqual([
        { type: 'text', content: 'Plain text without keywords' },
      ])
    })

    it('parses single keyword with surrounding text', () => {
      const result = parseKeywords('Apply 2 [Sinking] next turn')
      expect(result).toEqual([
        { type: 'text', content: 'Apply 2 ' },
        { type: 'keyword', content: 'Sinking' },
        { type: 'text', content: ' next turn' },
      ])
    })

    it('parses multiple keywords in text', () => {
      const result = parseKeywords('Apply [Sinking] and [Burn]')
      expect(result).toEqual([
        { type: 'text', content: 'Apply ' },
        { type: 'keyword', content: 'Sinking' },
        { type: 'text', content: ' and ' },
        { type: 'keyword', content: 'Burn' },
      ])
    })
  })

  describe('consecutive keywords', () => {
    it('parses two consecutive keywords [A][B]', () => {
      const result = parseKeywords('[Sinking][Burn]')
      expect(result).toEqual([
        { type: 'keyword', content: 'Sinking' },
        { type: 'keyword', content: 'Burn' },
      ])
    })

    it('parses three consecutive keywords', () => {
      const result = parseKeywords('[WhenUse][Sinking][OnKill]')
      expect(result).toEqual([
        { type: 'keyword', content: 'WhenUse' },
        { type: 'keyword', content: 'Sinking' },
        { type: 'keyword', content: 'OnKill' },
      ])
    })
  })

  describe('keyword position edge cases', () => {
    it('parses keyword at start of string', () => {
      const result = parseKeywords('[WhenUse] Deal damage')
      expect(result).toEqual([
        { type: 'keyword', content: 'WhenUse' },
        { type: 'text', content: ' Deal damage' },
      ])
    })

    it('parses keyword at end of string', () => {
      const result = parseKeywords('Inflict [Sinking]')
      expect(result).toEqual([
        { type: 'text', content: 'Inflict ' },
        { type: 'keyword', content: 'Sinking' },
      ])
    })

    it('parses keyword as only content', () => {
      const result = parseKeywords('[Sinking]')
      expect(result).toEqual([{ type: 'keyword', content: 'Sinking' }])
    })
  })

  describe('bracket edge cases', () => {
    it('treats empty brackets [] as text', () => {
      const result = parseKeywords('Text [] more text')
      // Empty brackets won't match the regex [^\]]+ (requires at least one char)
      expect(result).toEqual([{ type: 'text', content: 'Text [] more text' }])
    })

    it('handles nested brackets [[A]] - captures including inner bracket', () => {
      const result = parseKeywords('Text [[Nested]] more')
      // Regex [^\]]+ matches any char except ], so [[Nested] is captured as one match
      // The captured group is [Nested (including the opening bracket)
      expect(result).toEqual([
        { type: 'text', content: 'Text ' },
        { type: 'keyword', content: '[Nested' },
        { type: 'text', content: '] more' },
      ])
    })

    it('handles unclosed bracket', () => {
      const result = parseKeywords('Text [unclosed')
      expect(result).toEqual([{ type: 'text', content: 'Text [unclosed' }])
    })

    it('handles unopened bracket', () => {
      const result = parseKeywords('Text unopened]')
      expect(result).toEqual([{ type: 'text', content: 'Text unopened]' }])
    })
  })

  describe('regex state isolation', () => {
    it('handles multiple calls correctly (regex state reset)', () => {
      // Call multiple times to ensure regex lastIndex is reset
      const result1 = parseKeywords('[A] text')
      const result2 = parseKeywords('[B] other')

      expect(result1).toEqual([
        { type: 'keyword', content: 'A' },
        { type: 'text', content: ' text' },
      ])
      expect(result2).toEqual([
        { type: 'keyword', content: 'B' },
        { type: 'text', content: ' other' },
      ])
    })
  })
})

describe('resolveKeywordType', () => {
  it('returns battleKeyword for known battle keyword', () => {
    const result = resolveKeywordType('Sinking', mockBattleKeywords, mockSkillTags)
    expect(result).toBe('battleKeyword')
  })

  it('returns battleKeyword for another battle keyword', () => {
    const result = resolveKeywordType('Protection', mockBattleKeywords, mockSkillTags)
    expect(result).toBe('battleKeyword')
  })

  it('returns skillTag for known skill tag', () => {
    const result = resolveKeywordType('WhenUse', mockBattleKeywords, mockSkillTags)
    expect(result).toBe('skillTag')
  })

  it('returns skillTag for another skill tag', () => {
    const result = resolveKeywordType('OnSucceedAttack', mockBattleKeywords, mockSkillTags)
    expect(result).toBe('skillTag')
  })

  it('returns unknown for unrecognized key', () => {
    const result = resolveKeywordType('UnknownKeyword', mockBattleKeywords, mockSkillTags)
    expect(result).toBe('unknown')
  })

  it('returns unknown for empty string', () => {
    const result = resolveKeywordType('', mockBattleKeywords, mockSkillTags)
    expect(result).toBe('unknown')
  })

  it('prioritizes battleKeyword over skillTag if both have same key', () => {
    // If a key exists in both, battleKeyword is checked first
    const overlappingBattleKeywords = {
      ...mockBattleKeywords,
      WhenUse: { name: 'WhenUse', desc: 'Desc', iconId: null, buffType: 'Neutral' },
    }
    const result = resolveKeywordType('WhenUse', overlappingBattleKeywords, mockSkillTags)
    expect(result).toBe('battleKeyword')
  })
})

describe('getKeywordColor', () => {
  describe('battle keyword colors', () => {
    it('returns Negative color for Negative buffType', () => {
      const result = getKeywordColor('Sinking', 'battleKeyword', mockColorCodes, mockBattleKeywords)
      expect(result).toBe('#ff0000')
    })

    it('returns Positive color for Positive buffType', () => {
      const result = getKeywordColor('Protection', 'battleKeyword', mockColorCodes, mockBattleKeywords)
      expect(result).toBe('#00ff00')
    })

    it('returns Critical fallback when buffType not in colorCodes', () => {
      const keywordsWithUnknownBuff = {
        Weird: { name: 'Weird', desc: 'Weird', iconId: null, buffType: 'UnknownType' },
      }
      const result = getKeywordColor('Weird', 'battleKeyword', mockColorCodes, keywordsWithUnknownBuff)
      expect(result).toBe('#93f13e')
    })

    it('returns Critical fallback when keyword missing from battleKeywords', () => {
      const result = getKeywordColor('Missing', 'battleKeyword', mockColorCodes, mockBattleKeywords)
      expect(result).toBe('#93f13e')
    })
  })

  describe('skill tag colors', () => {
    it('returns key-specific color for known skill tag', () => {
      const result = getKeywordColor('WhenUse', 'skillTag', mockColorCodes, mockBattleKeywords)
      expect(result).toBe('#0000ff')
    })

    it('returns another key-specific color', () => {
      const result = getKeywordColor('OnSucceedAttack', 'skillTag', mockColorCodes, mockBattleKeywords)
      expect(result).toBe('#00ffff')
    })

    it('returns Critical fallback for skill tag without color entry', () => {
      const result = getKeywordColor('OnKill', 'skillTag', mockColorCodes, mockBattleKeywords)
      // OnKill is in skillTags but not in colorCodes
      expect(result).toBe('#93f13e')
    })
  })

  describe('unknown keyword colors', () => {
    it('returns empty string for unknown type', () => {
      const result = getKeywordColor('Whatever', 'unknown', mockColorCodes, mockBattleKeywords)
      expect(result).toBe('')
    })
  })

  describe('edge cases', () => {
    it('handles empty colorCodes with Critical fallback', () => {
      const emptyColorCodes: Record<string, string> = {}
      const result = getKeywordColor('Sinking', 'battleKeyword', emptyColorCodes, mockBattleKeywords)
      // No Critical in colorCodes, so returns '' via ?? ''
      expect(result).toBe('')
    })
  })
})

describe('resolveKeyword', () => {
  describe('battle keyword resolution', () => {
    it('returns full battle keyword data', () => {
      const result = resolveKeyword('Sinking', mockContext)

      expect(result).toEqual({
        type: 'battleKeyword',
        key: 'Sinking',
        displayText: 'Sinking',
        description: 'Each turn, lose HP equal to Sinking count.',
        iconId: 'Sinking',
        buffType: 'Negative',
        color: '#ff0000',
      })
    })

    it('returns battle keyword with null iconId', () => {
      const result = resolveKeyword('Burn', mockContext)

      expect(result.type).toBe('battleKeyword')
      expect(result.iconId).toBeNull()
      expect(result.displayText).toBe('Burn')
    })

    it('returns Positive battle keyword with correct color', () => {
      const result = resolveKeyword('Protection', mockContext)

      expect(result.type).toBe('battleKeyword')
      expect(result.color).toBe('#00ff00')
      expect(result.buffType).toBe('Positive')
    })
  })

  describe('skill tag resolution', () => {
    it('returns skill tag with display text', () => {
      const result = resolveKeyword('WhenUse', mockContext)

      expect(result).toEqual({
        type: 'skillTag',
        key: 'WhenUse',
        displayText: '[On Use]',
        color: '#0000ff',
      })
    })

    it('returns skill tag without description', () => {
      const result = resolveKeyword('OnSucceedAttack', mockContext)

      expect(result.type).toBe('skillTag')
      expect(result.displayText).toBe('[On Hit]')
      expect(result.description).toBeUndefined()
      expect(result.iconId).toBeUndefined()
    })
  })

  describe('unknown keyword resolution', () => {
    it('preserves brackets in displayText for unknown keywords', () => {
      const result = resolveKeyword('UnknownKey', mockContext)

      expect(result).toEqual({
        type: 'unknown',
        key: 'UnknownKey',
        displayText: '[UnknownKey]',
        color: '',
      })
    })

    it('returns unknown for empty key', () => {
      const result = resolveKeyword('', mockContext)

      expect(result.type).toBe('unknown')
      expect(result.displayText).toBe('[]')
    })
  })
})

describe('formatDescription', () => {
  it('returns empty array for empty string', () => {
    const result = formatDescription('', mockContext)
    expect(result).toEqual([])
  })

  it('returns text segment for plain text', () => {
    const result = formatDescription('Plain text', mockContext)
    expect(result).toEqual([{ type: 'text', content: 'Plain text' }])
  })

  it('attaches resolved keyword to keyword segments', () => {
    const result = formatDescription('Apply [Sinking]', mockContext)

    expect(result).toHaveLength(2)
    expect(result[0]).toEqual({ type: 'text', content: 'Apply ' })
    expect(result[1].type).toBe('keyword')
    expect(result[1].content).toBe('Sinking')
    expect(result[1].keyword).toBeDefined()
    expect(result[1].keyword?.type).toBe('battleKeyword')
    expect(result[1].keyword?.displayText).toBe('Sinking')
  })

  it('handles mixed battle keywords and skill tags', () => {
    const result = formatDescription('[WhenUse] Apply [Sinking]', mockContext)

    expect(result).toHaveLength(3)

    // First segment: skill tag
    expect(result[0].type).toBe('keyword')
    expect(result[0].keyword?.type).toBe('skillTag')
    expect(result[0].keyword?.displayText).toBe('[On Use]')

    // Second segment: text
    expect(result[1].type).toBe('text')
    expect(result[1].content).toBe(' Apply ')

    // Third segment: battle keyword
    expect(result[2].type).toBe('keyword')
    expect(result[2].keyword?.type).toBe('battleKeyword')
    expect(result[2].keyword?.displayText).toBe('Sinking')
  })

  it('handles unknown keywords in description', () => {
    const result = formatDescription('Text [Unknown] more', mockContext)

    expect(result).toHaveLength(3)
    expect(result[1].keyword?.type).toBe('unknown')
    expect(result[1].keyword?.displayText).toBe('[Unknown]')
  })

  it('handles consecutive keywords', () => {
    const result = formatDescription('[WhenUse][Sinking]', mockContext)

    expect(result).toHaveLength(2)
    expect(result[0].keyword?.type).toBe('skillTag')
    expect(result[1].keyword?.type).toBe('battleKeyword')
  })
})
