/**
 * Case matrix for the AbEventChoiceBranch tree: every next-target shape, every combination
 * of the inputs that decide the "nothing happened" fallback, and every branch label shape.
 */

import type { ComponentProps } from 'react'
import type { ChoiceBranch } from '../AbEventChoiceBranch'
import type { AbEventSelectionEvent } from '../../schemas/AbEventSchemas'
import type { CoinTossI18nContext } from '../../lib/abEventTextResolver'

type ChoiceBranchProps = ComponentProps<typeof ChoiceBranch>

// =============================================================================
// Rendering context
// =============================================================================

const I18N_CTX: CoinTossI18nContext = {
  affinityNames: { CRIMSON: 'Wrath', VIOLET: 'Envy' },
  unitKeywords: { Sinking: 'Sinking', LCB: 'LCB Sinner' },
  sinnerNames: { DonQuixote: 'Don Quixote' },
  identityNames: { '10310': 'The Manager' },
  successLabel: 'SUCCESS',
  failureLabel: 'FAILURE',
}

/** Marks the resolved effect so the assertion can tell which effect reached the DOM */
const resolveEffectText: ChoiceBranchProps['resolveEffectText'] = (
  effect,
  giftId,
  amount,
  target,
  condition,
  descId,
) => {
  if (effect === 'Nothing') return 'Nothing happened.'
  if (effect === 'Unresolvable') return null
  return `E[${effect}|${giftId ?? '-'}|${amount ?? '-'}|${target ?? '-'}|${condition ?? '-'}|${descId ?? '-'}]`
}

const processText = (text: string) => text.replace(/\{0\}/g, 'SINNER')

function judgement(threshold: number) {
  return {
    successThreshold: threshold,
    bestThreshold: threshold + 8,
    affinities: ['CRIMSON', 'VIOLET'],
  }
}

// =============================================================================
// Spec fixtures
// =============================================================================

const GIFT_EFFECT = { effect: 'GainGift', reward: { type: 'gift', id: '9001', num: 2, prob: 1 } }

/** Keyed by the last two digits of a nextEventId, parsed as an int */
export const SELECTION_EVENTS = {
  '1': {
    canSkip: false,
    judgement: judgement(12),
    adderInfo: [
      { correctionCase: 'SpecificUnit_DonQuixote', adder: 5 },
      { correctionCase: 'SpecificKeyword_Sinking', adder: -3 },
    ],
    results: [
      { outcome: 'SUCCESS', effects: [GIFT_EFFECT] },
      { outcome: 'FAILURE', effects: [{ effect: 'LoseHp', target: 'EveryAlly' }] },
    ],
  },
  // No judgement: present but renders nothing
  '2': { canSkip: false },
  '3': {
    canSkip: false,
    judgement: judgement(9),
    results: [
      {
        outcome: 'SUCCESS',
        effects: [],
        subResults: [
          { probability: 0.5, effects: [{ effect: 'SubA' }] },
          { condition: 'Prob_0.25', effects: [] },
          { effects: [{ effect: 'SubC' }], nextEventId: '901401' },
        ],
      },
      { outcome: 'FAILURE', effects: [{ effect: 'Flop' }] },
    ],
  },
  '4': {
    canSkip: false,
    judgement: judgement(11),
    results: [
      { outcome: 'SUCCESS', effects: [{ effect: 'Win' }], nextEventId: '901205' },
      { outcome: 'FAILURE', effects: [{ effect: 'Lose' }], nextEventId: '901301' },
    ],
  },
  '5': {
    canSkip: false,
    judgement: judgement(15),
    results: [{ outcome: 'FAILURE', effects: [{ effect: 'Chained' }] }],
  },
  '6': {
    canSkip: false,
    judgement: judgement(13),
    results: [
      { outcome: 'SUCCESS', effects: [], nextEventId: '901299' },
      {
        outcome: 'FAILURE',
        effects: [],
        nextEventId: '901301',
        subResults: [{ effects: [{ effect: 'Sup' }], nextEventId: '901401' }],
      },
    ],
  },
} satisfies Record<string, AbEventSelectionEvent>

export const SELECTION_TEXTS = {
  '1': { behaveDesc: 'Parent toss 1', successDesc: 'you win', failureDesc: ['you lose'] },
  '3': { behaveDesc: 'PARENT-3', successDesc: ['p1', 'p2', 'p3'] },
  '4': { behaveDesc: 'Parent toss 4' },
  '5': { behaveDesc: 'Parent toss 5' },
  '6': { behaveDesc: 'Parent toss 6', failureDesc: ['f1'] },
} satisfies NonNullable<ChoiceBranchProps['allSelectionTexts']>

const SUB_EVENTS: NonNullable<ChoiceBranchProps['subEvents']> = {
  '901301': {
    choices: [
      { index: 0, directEffects: [{ effect: 'Heal' }] },
      { index: 1 },
      { index: 2, nextEventId: '901301' },
      { index: 3, nextEventId: '901303' },
      { index: 4, nextEventId: '901307' },
      { index: 5, nextEventId: '901401' },
      { index: 6, nextEventId: '901399' },
      { index: 7, nextEventId: '901302' },
      {
        index: 8,
        probabilityResults: [
          { probability: 0.5, effects: [{ effect: 'P1' }], nextEventId: '901401' },
          { probability: 0.5, effects: [] },
        ],
      },
      {
        index: 9,
        conditionalResults: [
          { condition: 'MpAverage_Under30', effects: [] },
          { effects: [{ effect: 'Q' }] },
        ],
      },
    ],
    // '3' shadows the parent's '3'; '7' exists only here
    selectionEvents: {
      '3': {
        canSkip: false,
        judgement: judgement(19),
        results: [{ outcome: 'SUCCESS', effects: [{ effect: 'LocalWin' }] }],
      },
      '7': {
        canSkip: false,
        judgement: judgement(7),
        results: [{ outcome: 'FAILURE', effects: [{ effect: 'LocalFlop' }] }],
      },
    },
  },
  '901401': { choices: [{ index: 0, directEffects: [{ effect: 'Deep' }] }] },
}

const SUB_EVENT_TEXTS: NonNullable<ChoiceBranchProps['subEventTexts']> = {
  '901301': {
    desc: 'Sub event desc <color=#ebcaa2>{0}</color>',
    options: [
      { message: 'Sub 0', result: ['single narrative'] },
      { message: 'Sub 1' },
      { message: 'Sub 2' },
      { message: 'Sub 3' },
      { message: 'Sub 4' },
      { message: 'Sub 5' },
      { message: 'Sub 6' },
      { message: 'Sub 7' },
      { message: 'Sub 8', messageDesc: 'eight', result: ['first', 'second'] },
      { message: 'Sub 9', result: ['cond one', 'cond two'] },
    ],
    selectionTexts: { '3': { behaveDesc: 'LOCAL-3', successDesc: ['l1'] } },
  },
  '901401': { desc: 'Deep sub event desc' },
}

// =============================================================================
// ChoiceBranch case matrix
// =============================================================================

export function baseProps(): ChoiceBranchProps {
  return {
    choice: { index: 0 },
    processText,
    resolveEffectText,
    allSelectionEvents: SELECTION_EVENTS,
    allSelectionTexts: SELECTION_TEXTS,
    subEvents: SUB_EVENTS,
    subEventTexts: SUB_EVENT_TEXTS,
    i18nCtx: I18N_CTX,
  }
}

const BASE_PROPS = baseProps()

const DIRECT_EFFECTS = [{ effect: 'Direct' }, { effect: 'Unresolvable' }]
const PROBABILITY_RESULTS = [
  { probability: 0.5, effects: [{ effect: 'PR' }] },
  { probability: 0.5, effects: [{ effect: 'PR2' }] },
]
const CONDITIONAL_RESULTS = [
  { condition: 'Prob_0.5', effects: [{ effect: 'CR' }] },
  { effects: [{ effect: 'CR2' }], nextEventId: '901401' },
]
/** Empty-effect branches make BranchCard fall back to its own "nothing happened" line */
const EMPTY_BRANCH_RESULTS = [
  { probability: 0.5, effects: [] },
  { probability: 0.5, effects: [{ effect: 'PR2' }] },
]

export interface ChoiceBranchCase {
  name: string
  /** Which of the five suppressors of the "nothing happened" line are set */
  flags?: string[]
  props: ChoiceBranchProps
}

/** All 32 combinations of the five inputs that decide the "nothing happened" fallback */
function emptinessMatrix(): ChoiceBranchCase[] {
  const cases: ChoiceBranchCase[] = []
  for (let mask = 0; mask < 32; mask++) {
    const flags: string[] = []
    const props: ChoiceBranchProps = { ...BASE_PROPS, choice: { index: mask } }
    if (mask & 1) {
      props.choice = { ...props.choice, directEffects: DIRECT_EFFECTS }
      flags.push('directEffects')
    }
    if (mask & 2) {
      props.choice = { ...props.choice, probabilityResults: PROBABILITY_RESULTS }
      flags.push('probabilityResults')
    }
    if (mask & 4) {
      props.choice = { ...props.choice, conditionalResults: CONDITIONAL_RESULTS }
      flags.push('conditionalResults')
    }
    if (mask & 8) {
      props.selectionEvent = SELECTION_EVENTS['1']
      props.selectionText = SELECTION_TEXTS['1']
      flags.push('selectionEvent')
    }
    if (mask & 16) {
      props.linkedSubEventId = '901301'
      flags.push('linkedSubEventId')
    }
    cases.push({ name: `emptiness mask ${mask} [${flags.join(',') || 'none'}]`, flags, props })
  }
  return cases
}

export const EMPTINESS_CASES = emptinessMatrix()

export const STRUCTURAL_CASES: ChoiceBranchCase[] = [
  // Suppressors that render nothing themselves
  {
    name: 'selection event without judgement suppresses the fallback',
    flags: ['selectionEvent'],
    props: { ...BASE_PROPS, selectionEvent: SELECTION_EVENTS['2'] },
  },
  {
    name: 'linked sub-event id missing from subEvents suppresses the fallback',
    flags: ['linkedSubEventId'],
    props: { ...BASE_PROPS, linkedSubEventId: '999999' },
  },

  // Narrative / branch label shapes
  {
    name: 'single narrative result',
    props: { ...BASE_PROPS, option: { message: 'Take it', result: ['one line'] } },
  },
  {
    name: 'multi narrative over probability results',
    props: {
      ...BASE_PROPS,
      choice: { index: 1, probabilityResults: PROBABILITY_RESULTS },
      option: { message: 'Roll', messageDesc: 'desc', result: ['win text', 'lose text'] },
    },
  },
  {
    name: 'multi narrative over conditional results',
    props: {
      ...BASE_PROPS,
      choice: { index: 2, conditionalResults: CONDITIONAL_RESULTS },
      option: { message: 'Check', result: ['cond text', 'plain text'] },
    },
  },
  {
    name: 'multi narrative with no matching result rows',
    props: { ...BASE_PROPS, option: { message: 'Bare', result: ['a', 'b', 'c'] } },
  },
  {
    name: 'probability results without narrative',
    props: { ...BASE_PROPS, choice: { index: 3, probabilityResults: PROBABILITY_RESULTS } },
  },
  {
    name: 'branch card falls back when its own effects are empty',
    props: {
      ...BASE_PROPS,
      choice: { index: 4, probabilityResults: EMPTY_BRANCH_RESULTS },
      option: { message: 'Empty branch', result: ['first', 'second'] },
    },
  },

  // Coin toss shapes
  {
    name: 'coin toss with adders and plain outcomes',
    props: {
      ...BASE_PROPS,
      selectionEvent: SELECTION_EVENTS['1'],
      selectionText: SELECTION_TEXTS['1'],
    },
  },
  {
    name: 'coin toss with sub-result labels (probability, condition, neither)',
    props: {
      ...BASE_PROPS,
      selectionEvent: SELECTION_EVENTS['3'],
      selectionText: SELECTION_TEXTS['3'],
    },
  },
  {
    name: 'coin toss outcomes chaining to a coin toss and to a sub-event',
    props: {
      ...BASE_PROPS,
      selectionEvent: SELECTION_EVENTS['4'],
      selectionText: SELECTION_TEXTS['4'],
    },
  },
  {
    name: 'coin toss outcome with unresolvable next id and with sub-results suppressing it',
    props: {
      ...BASE_PROPS,
      selectionEvent: SELECTION_EVENTS['6'],
      selectionText: SELECTION_TEXTS['6'],
    },
  },
  {
    name: 'coin toss without selection text',
    props: { ...BASE_PROPS, selectionEvent: SELECTION_EVENTS['1'] },
  },

  // Sub-event tree
  {
    name: 'linked sub-event renders every next-target shape',
    props: { ...BASE_PROPS, linkedSubEventId: '901301' },
  },
  {
    name: 'linked sub-event without sub-event texts',
    props: { ...BASE_PROPS, linkedSubEventId: '901301', subEventTexts: undefined },
  },
  {
    name: 'linked sub-event with no selection events in scope',
    props: {
      ...BASE_PROPS,
      linkedSubEventId: '901301',
      allSelectionEvents: undefined,
      allSelectionTexts: undefined,
    },
  },
]
