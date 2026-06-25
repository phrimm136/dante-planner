import { describe, it, expect } from 'vitest'
import {
  resolveCondition,
  createEffectTextResolver,
  formatAdderInfo,
  AFFINITY_COLORS,
} from '../abEventTextResolver'
import type { AbEventShared } from '../../schemas/AbEventSchemas'

// =============================================================================
// Test Fixtures
// =============================================================================

function createShared(overrides: Partial<AbEventShared> = {}): AbEventShared {
  return {
    effects: {
      Nothing: '<color=#ebcaa2>Nothing happened.</color>',
      GetConfirmedEgogift: '<color=#ebcaa2>E.G.O Gift {reward} acquired!</color>',
      LoseHpOnly: '<color=#ebcaa2>{conditions}{targets}</color> <color=#e30000>{amount} HP</color> damage!',
      RecoverHpMpSameAmount: '<color=#ebcaa2>{targets}</color> heal <color=#00ff9c>{amount} HP</color> and SP',
      RecoverHpMpDifferentAmount: 'Heal <color=#00ff9c>{hpAmount} HP</color> and <color=#80c9ff>{mpAmount} SP</color>',
      Choice_901007: 'Pre-baked effect text via descId',
      ...overrides.effects,
    },
    targets: {
      Ally: 'Friendly Units',
      RandomAlly: 'Random Friendly Units',
      EveryAlly: 'All Allies',
      LowestHpAlly: 'Friendly Units with the least HP',
      ChosenPersonality: 'The Selected Identity',
      ...overrides.targets,
    },
    keywords: {
      IncludeSkillAttribute: '[ATTRIBUTES] Affinity Attack Skill',
      NotIncludeSkillAttribute: '[ATTRIBUTES] Affinity Attack Skill (not)',
      SomeKeyword: 'A keyword condition',
      ...overrides.keywords,
    },
    affinities: {
      CRIMSON: 'Wrath',
      SCARLET: 'Lust',
      VIOLET: 'Envy',
      ...overrides.affinities,
    },
    sinnerNames: {
      DonQuixote: 'Don Quixote',
      Ryoshu: 'Ryōshu',
      Faust: 'Faust',
      ...overrides.sinnerNames,
    },
    ...overrides,
  }
}

const GIFT_NAMES: Record<string, string> = {
  '9001': 'Bleeding Blade',
  '9023': 'Lightning Branch',
}

// =============================================================================
// resolveCondition
// =============================================================================

describe('resolveCondition', () => {
  const shared = createShared()

  it('returns keyword lookup for direct keyword match', () => {
    const result = resolveCondition('SomeKeyword', shared)
    expect(result).toContain('A keyword condition')
  })

  it('resolves IncludeSkillAttribute with single affinity', () => {
    const result = resolveCondition('IncludeSkillAttribute_CRIMSON', shared)
    expect(result).toContain('Wrath')
    expect(result).toContain(AFFINITY_COLORS.CRIMSON)
  })

  it('resolves NotIncludeSkillAttribute with multiple affinities', () => {
    const result = resolveCondition('NotIncludeSkillAttribute_CRIMSON,VIOLET', shared)
    expect(result).toContain('Wrath')
    expect(result).toContain('Envy')
    expect(result).toContain(AFFINITY_COLORS.CRIMSON)
    expect(result).toContain(AFFINITY_COLORS.VIOLET)
  })

  it('returns raw condition for unknown conditions', () => {
    expect(resolveCondition('UnknownCondition', shared)).toBe('UnknownCondition')
  })

  it('handles missing affinity name gracefully', () => {
    const result = resolveCondition('IncludeSkillAttribute_UNKNOWN', shared)
    expect(result).toContain('UNKNOWN')
  })
})

// =============================================================================
// createEffectTextResolver
// =============================================================================

describe('createEffectTextResolver', () => {
  const shared = createShared()
  const resolve = createEffectTextResolver(shared, GIFT_NAMES)

  describe('descId lookup', () => {
    it('returns pre-baked text when descId matches', () => {
      expect(resolve('anything', undefined, undefined, undefined, undefined, 'Choice_901007'))
        .toBe('Pre-baked effect text via descId')
    })

    it('falls back to effect type when descId not found', () => {
      const result = resolve('Nothing', undefined, undefined, undefined, undefined, 'NonExistentDescId')
      expect(result).toContain('Nothing happened')
    })
  })

  describe('effect type resolution', () => {
    it('resolves exact effect type match', () => {
      const result = resolve('Nothing')
      expect(result).toContain('Nothing happened')
    })

    it('returns null for unknown effect type', () => {
      expect(resolve('CompletelyUnknownEffect')).toBeNull()
    })
  })

  describe('numeric suffix stripping', () => {
    it('strips single numeric suffix for template lookup', () => {
      const result = resolve('LoseHpOnly_10')
      expect(result).not.toBeNull()
    })

    it('strips multiple numeric suffixes (hpAmount/mpAmount)', () => {
      const result = resolve('RecoverHpMpDifferentAmount_12_10')
      expect(result).toContain('12')
      expect(result).toContain('10')
    })

    it('uses extracted amount in template', () => {
      const result = resolve('RecoverHpMpSameAmount_15')
      expect(result).toContain('15')
    })
  })

  describe('reward substitution', () => {
    it('substitutes gift name in {reward} placeholder', () => {
      const result = resolve('GetConfirmedEgogift', 9001)
      expect(result).toContain('Bleeding Blade')
    })

    it('falls back to gift ID when name not found', () => {
      const result = resolve('GetConfirmedEgogift', 99999)
      expect(result).toContain('99999')
    })
  })

  describe('target resolution', () => {
    it('resolves known target from targets dict', () => {
      const result = resolve('LoseHpOnly_10', undefined, undefined, 'EveryAlly')
      expect(result).toContain('All Allies')
    })

    it('strips _N suffix from targets before lookup', () => {
      const result = resolve('LoseHpOnly_10', undefined, undefined, 'RandomAlly_1')
      expect(result).toContain('Random Friendly Units')
    })

    it('falls back to sinnerNames for sinner-as-target', () => {
      const result = resolve('LoseHpOnly_10', undefined, undefined, 'DonQuixote')
      expect(result).toContain('Don Quixote')
    })

    it('returns raw target when not found in any dict', () => {
      const result = resolve('LoseHpOnly_10', undefined, undefined, 'UnknownTarget')
      expect(result).toContain('UnknownTarget')
    })
  })

  describe('condition resolution in templates', () => {
    it('resolves condition and target together', () => {
      const result = resolve(
        'LoseHpOnly_10',
        undefined, undefined,
        'EveryAlly',
        'NotIncludeSkillAttribute_CRIMSON',
      )
      expect(result).toContain('Wrath')
      expect(result).toContain('All Allies')
    })

    it('handles no condition (empty conditions placeholder)', () => {
      const result = resolve('LoseHpOnly_10', undefined, undefined, 'EveryAlly')
      expect(result).toContain('All Allies')
      expect(result).not.toContain('{conditions}')
    })
  })

  describe('amount from explicit parameter', () => {
    it('uses explicit amount over suffix-extracted amount', () => {
      const result = resolve('RecoverHpMpSameAmount_15', undefined, 25)
      expect(result).toContain('25')
    })
  })
})

// =============================================================================
// formatAdderInfo
// =============================================================================

describe('formatAdderInfo', () => {
  const unitKeywords: Record<string, string> = {
    Sinking: 'Sinking',
    Bleed: 'Bleed',
    LCB: 'LCB Sinner',
    SevenAssoc: 'Seven Association',
  }
  const sinnerNames: Record<string, string> = {
    DonQuixote: 'Don Quixote',
    Ryoshu: 'Ryōshu',
  }
  const identityNames: Record<string, string> = {
    '10310': 'The Manager of La Manchaland',
    '10911': 'The Princess of La Manchaland',
    '11210': 'The Priest of La Manchaland',
  }

  it('resolves SpecificAssociation with single key', () => {
    const result = formatAdderInfo(
      [{ correctionCase: 'SpecificAssociation_LCB', adder: 5 }],
      unitKeywords, sinnerNames,
    )
    expect(result).toEqual(['LCB Sinner +5'])
  })

  it('resolves SpecificAssociation with multiple keys', () => {
    const result = formatAdderInfo(
      [{ correctionCase: 'SpecificAssociation_LCB,SevenAssoc', adder: 3 }],
      unitKeywords, sinnerNames,
    )
    expect(result).toEqual(['LCB Sinner / Seven Association +3'])
  })

  it('resolves SpecificKeyword', () => {
    const result = formatAdderInfo(
      [{ correctionCase: 'SpecificKeyword_Sinking', adder: -3 }],
      unitKeywords, sinnerNames,
    )
    expect(result).toEqual(['Sinking -3'])
  })

  it('resolves SpecificUnit with sinner name', () => {
    const result = formatAdderInfo(
      [{ correctionCase: 'SpecificUnit_DonQuixote', adder: 5 }],
      unitKeywords, sinnerNames,
    )
    expect(result).toEqual(['Don Quixote +5'])
  })

  it('resolves SpecificPersonality with identity names', () => {
    const result = formatAdderInfo(
      [{ correctionCase: 'SpecificPersonality_10310', adder: -10 }],
      unitKeywords, sinnerNames, identityNames,
    )
    expect(result).toEqual(['The Manager of La Manchaland -10'])
  })

  it('resolves SpecificPersonality with multiple IDs', () => {
    const result = formatAdderInfo(
      [{ correctionCase: 'SpecificPersonality_10310,10911,11210', adder: 7 }],
      unitKeywords, sinnerNames, identityNames,
    )
    expect(result[0]).toContain('The Manager of La Manchaland')
    expect(result[0]).toContain('The Princess of La Manchaland')
    expect(result[0]).toContain('The Priest of La Manchaland')
    expect(result[0]).toContain('+7')
  })

  it('falls back to raw ID when identity name not found', () => {
    const result = formatAdderInfo(
      [{ correctionCase: 'SpecificPersonality_99999', adder: 1 }],
      unitKeywords, sinnerNames, identityNames,
    )
    expect(result).toEqual(['99999 +1'])
  })

  it('falls back to raw ID when identityNames not provided', () => {
    const result = formatAdderInfo(
      [{ correctionCase: 'SpecificPersonality_10310', adder: 1 }],
      unitKeywords, sinnerNames,
    )
    expect(result).toEqual(['10310 +1'])
  })

  it('falls back to raw key for SpecificUnit with unknown sinner', () => {
    const result = formatAdderInfo(
      [{ correctionCase: 'SpecificUnit_UnknownSinner', adder: 2 }],
      unitKeywords, sinnerNames,
    )
    expect(result).toEqual(['UnknownSinner +2'])
  })

  it('formats negative adders without + sign', () => {
    const result = formatAdderInfo(
      [{ correctionCase: 'SpecificKeyword_Bleed', adder: -5 }],
      unitKeywords, sinnerNames,
    )
    expect(result).toEqual(['Bleed -5'])
  })

  it('handles multiple adder entries', () => {
    const result = formatAdderInfo(
      [
        { correctionCase: 'SpecificUnit_DonQuixote', adder: 5 },
        { correctionCase: 'SpecificKeyword_Sinking', adder: -3 },
      ],
      unitKeywords, sinnerNames,
    )
    expect(result).toHaveLength(2)
    expect(result[0]).toContain('Don Quixote')
    expect(result[1]).toContain('Sinking')
  })

  it('falls back to underscore-to-space for unknown SpecificKeyword', () => {
    const result = formatAdderInfo(
      [{ correctionCase: 'SpecificKeyword_Some_Unknown_Key', adder: 1 }],
      unitKeywords, sinnerNames,
    )
    expect(result[0]).toContain('Some Unknown Key')
  })
})
