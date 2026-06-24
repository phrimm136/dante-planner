import { describe, it, expect } from 'vitest'
import {
  AbEventSpecListEntrySchema,
  AbEventSpecListSchema,
  AbEventDataSchema,
  AbEventI18nSchema,
  AbEventSharedSchema,
} from '../AbEventSchemas'

describe('AbEventSpecListEntrySchema', () => {
  it('accepts valid entry', () => {
    const result = AbEventSpecListEntrySchema.safeParse({
      relatedEgoGifts: ['9001', '991002'],
      relatedThemePacks: ['1002', '1003'],
      hasImage: true,
    })
    expect(result.success).toBe(true)
  })

  it('accepts entry with empty arrays', () => {
    const result = AbEventSpecListEntrySchema.safeParse({
      relatedEgoGifts: [],
      relatedThemePacks: [],
      hasImage: false,
    })
    expect(result.success).toBe(true)
  })

  it('rejects missing hasImage', () => {
    const result = AbEventSpecListEntrySchema.safeParse({
      relatedEgoGifts: [],
      relatedThemePacks: [],
    })
    expect(result.success).toBe(false)
  })

  it('rejects an unknown key under strict parsing', () => {
    const result = AbEventSpecListEntrySchema.strict().safeParse({
      relatedEgoGifts: [],
      relatedThemePacks: [],
      hasImage: false,
      unexpectedKey: 'nope',
    })
    expect(result.success).toBe(false)
  })
})

describe('AbEventSpecListSchema', () => {
  it('accepts valid spec list', () => {
    const result = AbEventSpecListSchema.safeParse({
      '901001': {
        relatedEgoGifts: ['9001'],
        relatedThemePacks: ['1002'],
        hasImage: true,
      },
      '971055': {
        relatedEgoGifts: [],
        relatedThemePacks: [],
        hasImage: false,
      },
    })
    expect(result.success).toBe(true)
  })
})



describe('AbEventDataSchema', () => {
  it('accepts action event with choices', () => {
    const result = AbEventDataSchema.safeParse({
      canSkip: false,
      eventType: 'EVENT',
      choices: [
        {
          index: 0,
          directEffects: [
            {
              effect: 'GetConfirmedEgogift',
              reward: { type: 'EGO_GIFT', id: 9001, num: 1, prob: 1 },
            },
          ],
        },
        { index: 1, nextEventId: 90100201 },
      ],
    })
    expect(result.success).toBe(true)
  })

  it('accepts event with selectionEvents (coin toss)', () => {
    const result = AbEventDataSchema.safeParse({
      canSkip: false,
      eventType: 'EVENT',
      choices: [{ index: 0, nextEventId: 90100201 }],
      selectionEvents: {
        '1': {
          canSkip: false,
          eventType: 'COIN_EVENT',
          participantInfo: { min: 1, max: 1 },
          judgement: {
            successThreshold: 7,
            bestThreshold: 14,
            affinities: ['CRIMSON', 'AZURE'],
          },
          results: [
            {
              outcome: 'SUCCESS',
              effects: [
                { effect: 'GetConfirmedEgogift', reward: { type: 'EGO_GIFT', id: 9002, num: 1, prob: 1.0 } },
              ],
            },
            {
              outcome: 'FAILURE',
              effects: [
                { effect: 'LoseHpMpDifferentAmount_12_8', target: 'ChosenPersonality', condition: 'FAILURE' },
              ],
            },
          ],
        },
      },
    })
    expect(result.success).toBe(true)
  })

  it('accepts minimal event', () => {
    const result = AbEventDataSchema.safeParse({
      canSkip: false,
      eventType: 'EVENT',
    })
    expect(result.success).toBe(true)
  })

  it('accepts event with cantSelectInThisCase', () => {
    const result = AbEventDataSchema.safeParse({
      canSkip: false,
      eventType: 'EVENT',
      choices: [{ index: 0, cantSelectInThisCase: 'HasNotEgoGift_9723' }],
    })
    expect(result.success).toBe(true)
  })

  it('accepts choice with conditionalResults', () => {
    const result = AbEventDataSchema.safeParse({
      canSkip: false,
      eventType: 'EVENT',
      choices: [{
        index: 0,
        conditionalResults: [
          { condition: 'MpAverage_Under0', effects: [] },
          { condition: 'MpAverage_NotLessThan0', effects: [{ effect: 'RecoverHpMpSameAmount_15', target: 'EveryAlly' }] },
        ],
      }],
    })
    expect(result.success).toBe(true)
  })

  it('accepts conditionalResult without condition (optional)', () => {
    const result = AbEventDataSchema.safeParse({
      canSkip: false,
      eventType: 'EVENT',
      choices: [{
        index: 0,
        conditionalResults: [
          { probability: 0.5, effects: [{ effect: 'GetConfirmedEgogift' }] },
          { effects: [{ effect: 'RecoverHpMpSameAmount_25' }] },
        ],
      }],
    })
    expect(result.success).toBe(true)
  })

  it('accepts choice with probabilityResults', () => {
    const result = AbEventDataSchema.safeParse({
      canSkip: false,
      eventType: 'EVENT',
      choices: [{
        index: 0,
        probabilityResults: [
          { probability: 0.5, effects: [{ effect: 'GetConfirmedEgogift' }] },
          { probability: 0.5, effects: [] },
        ],
      }],
    })
    expect(result.success).toBe(true)
  })

  it('accepts coin toss result with subResults', () => {
    const result = AbEventDataSchema.safeParse({
      canSkip: false,
      eventType: 'EVENT',
      selectionEvents: {
        '1': {
          canSkip: false,
          judgement: { successThreshold: 8, bestThreshold: 16, affinities: ['SCARLET'] },
          results: [{
            outcome: 'FAILURE',
            effects: [{ effect: 'LoseHpOnly_5' }],
            subResults: [
              { condition: 'Failed_Under3', effects: [{ effect: 'LoseHpOnly_5' }], nextEventId: 901020 },
              { condition: 'Failed_NotLessThan3', effects: [{ effect: 'LoseHpOnly_10' }] },
            ],
          }],
        },
      },
    })
    expect(result.success).toBe(true)
  })

  it('accepts subResults with probability instead of condition', () => {
    const result = AbEventDataSchema.safeParse({
      canSkip: false,
      eventType: 'EVENT',
      selectionEvents: {
        '1': {
          canSkip: false,
          judgement: { successThreshold: 8, bestThreshold: 16, affinities: ['SCARLET'] },
          results: [{
            outcome: 'SUCCESS',
            effects: [],
            subResults: [
              { probability: 0.5, effects: [{ effect: 'GetConfirmedEgogift' }] },
              { probability: 0.5, effects: [{ effect: 'GetConfirmedEgogift' }] },
            ],
          }],
        },
      },
    })
    expect(result.success).toBe(true)
  })

  it('rejects missing eventType', () => {
    const result = AbEventDataSchema.safeParse({ canSkip: false })
    expect(result.success).toBe(false)
  })
})

describe('AbEventI18nSchema', () => {
  it('accepts action event i18n', () => {
    const result = AbEventI18nSchema.safeParse({
      name: 'Hellterfly',
      desc: 'Orange circles float...',
      options: [
        { message: 'Reach out.', messageDesc: 'Select to gain a Burn E.G.O Gift' },
        { message: 'Turn around.', result: ['Resisting the temptation...'] },
      ],
      choiceEffects: { '0': ['Identities without Wrath take 16 HP damage'] },
    })
    expect(result.success).toBe(true)
  })

  it('accepts personality event i18n with selectionTexts', () => {
    const result = AbEventI18nSchema.safeParse({
      selectionTexts: {
        '1': {
          title: 'Test Event',
          behaveDesc: 'Who should go forth?',
          successDesc: ['Success text'],
          failureDesc: ['Failure text'],
        },
      },
    })
    expect(result.success).toBe(true)
  })

  it('accepts empty i18n object', () => {
    expect(AbEventI18nSchema.safeParse({}).success).toBe(true)
  })
})

describe('AbEventSharedSchema', () => {
  it('accepts valid shared data', () => {
    const result = AbEventSharedSchema.safeParse({
      effects: {
        Nothing: '<color=#ebcaa2>Nothing happened.</color>',
        GetConfirmedEgogift: '<color=#ebcaa2>E.G.O Gift {reward} obtained!</color>',
      },
      targets: {
        EveryAlly: 'All Allies',
        ChosenPersonality: 'The Selected Identity',
      },
      keywords: {
        IncludeSkillAttribute: 'with the [ATTRIBUTES] Sin Affinity Attack Skill',
      },
    })
    expect(result.success).toBe(true)
  })

  it('accepts shared data with resultLogs', () => {
    const result = AbEventSharedSchema.safeParse({
      effects: {},
      targets: {},
      keywords: {},
      resultLogs: { '901001_test': 'Log content' },
    })
    expect(result.success).toBe(true)
  })

  it('accepts shared data with identityNames', () => {
    const result = AbEventSharedSchema.safeParse({
      effects: {},
      targets: {},
      keywords: {},
      identityNames: { '10310': 'The Manager of La Manchaland' },
    })
    expect(result.success).toBe(true)
  })

  it('accepts shared data with sinnerNames', () => {
    const result = AbEventSharedSchema.safeParse({
      effects: {},
      targets: {},
      keywords: {},
      sinnerNames: { DonQuixote: 'Don Quixote' },
    })
    expect(result.success).toBe(true)
  })

  it('rejects missing required fields', () => {
    expect(AbEventSharedSchema.safeParse({ effects: {} }).success).toBe(false)
    expect(AbEventSharedSchema.safeParse({}).success).toBe(false)
  })
})
