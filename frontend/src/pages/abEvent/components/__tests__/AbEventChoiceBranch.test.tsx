import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { ChoiceBranch } from '../AbEventChoiceBranch'
import {
  EMPTINESS_CASES,
  SELECTION_EVENTS,
  SELECTION_TEXTS,
  STRUCTURAL_CASES,
  baseProps,
} from './abEventFixtures'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, options?: Record<string, unknown>) =>
        options ? `${key}:${JSON.stringify(options)}` : key,
      i18n: { language: 'EN' },
    }),
  }
})

// =============================================================================
// DOM helpers — classes are matched exactly so a nested block never masquerades
// as the block being asserted on
// =============================================================================

const CHOICE_BODY = 'p-4 space-y-3'
const SUB_CHOICE_BODY = 'p-3 space-y-2'
const CARD = 'border border-border rounded-md overflow-hidden'
const BRANCH_CARD = 'border border-border rounded-sm overflow-hidden'
const COIN_TOSS_OUTCOME = 'px-4 py-3 border-b border-border last:border-b-0'
const NEXT_EVENT_WRAPPER = 'mt-3'
const FALLBACK = 'text-sm'

function divsWithClass(root: ParentNode, className: string): HTMLElement[] {
  return Array.from(root.querySelectorAll('div')).filter(
    (el) => el.getAttribute('class') === className,
  )
}

function childrenWithClass(root: Element, className: string): Element[] {
  return Array.from(root.children).filter((el) => el.getAttribute('class') === className)
}

/** The "nothing happened" line the choice itself renders, not one from a nested branch */
function hasOwnFallback(root: Element | undefined): boolean {
  if (!root) return false
  return childrenWithClass(root, FALLBACK).some((el) => el.textContent === 'Nothing happened.')
}

function choiceBody(container: HTMLElement): Element {
  const [body] = divsWithClass(container, CHOICE_BODY)
  if (!body) throw new Error('choice body not found')
  return body
}

/** Locate a sub-event choice by its header message */
function subChoiceBody(container: HTMLElement, message: string): Element {
  const card = divsWithClass(container, CARD).find((el) => {
    const header = el.children[0]
    return (
      header?.getAttribute('class')?.startsWith('bg-muted/50') === true &&
      header.children[0]?.textContent === message
    )
  })
  if (!card) throw new Error(`sub choice "${message}" not found`)
  const [body] = childrenWithClass(card, SUB_CHOICE_BODY)
  if (!body) throw new Error(`sub choice "${message}" has no body`)
  return body
}

/** The outcome panels of the choice's own coin toss, ignoring any it chains into */
function coinTossOutcomes(container: HTMLElement): Element[] {
  const [section] = childrenWithClass(choiceBody(container), CARD)
  if (!section) throw new Error('coin toss section not found')
  return childrenWithClass(section, COIN_TOSS_OUTCOME)
}

function branchLabels(root: ParentNode): (string | null)[] {
  return divsWithClass(root, BRANCH_CARD).map((card) => {
    const first = card.children[0]
    return first?.getAttribute('class')?.startsWith('bg-muted/30') === true
      ? first.textContent
      : null
  })
}

function coinTossThresholds(root: ParentNode): string[] {
  return Array.from(root.querySelectorAll('span'))
    .filter((el) => el.getAttribute('class') === 'text-xs font-semibold text-foreground ml-auto')
    .map((el) => el.textContent ?? '')
}

// =============================================================================
// Nothing-happened fallback
// =============================================================================

describe('ChoiceBranch nothing-happened fallback', () => {
  for (const testCase of EMPTINESS_CASES) {
    it(`shows the fallback only with no outcome: ${testCase.name}`, () => {
      const { container } = render(<ChoiceBranch {...testCase.props} />)

      expect(hasOwnFallback(choiceBody(container))).toBe(testCase.flags?.length === 0)
    })
  }

  it('stays suppressed by a selection event that carries no judgement', () => {
    const { container } = render(
      <ChoiceBranch {...baseProps()} selectionEvent={SELECTION_EVENTS['2']} />,
    )

    expect(hasOwnFallback(choiceBody(container))).toBe(false)
    expect(divsWithClass(container, CARD)).toHaveLength(0)
  })

  it('stays suppressed by a linked sub-event id that resolves to nothing', () => {
    const { container } = render(<ChoiceBranch {...baseProps()} linkedSubEventId="999999" />)

    expect(hasOwnFallback(choiceBody(container))).toBe(false)
    expect(divsWithClass(container, CARD)).toHaveLength(0)
  })

  it('falls back inside a branch card whose own effects are empty', () => {
    const { container } = render(
      <ChoiceBranch
        {...baseProps()}
        choice={{
          index: 0,
          probabilityResults: [
            { probability: 0.5, effects: [] },
            { probability: 0.5, effects: [{ effect: 'PR2' }] },
          ],
        }}
        option={{ message: 'Empty branch', result: ['first', 'second'] }}
      />,
    )

    const [emptyCard, filledCard] = divsWithClass(container, BRANCH_CARD)
    if (!emptyCard || !filledCard) throw new Error('expected an empty and a filled branch card')
    expect(hasOwnFallback(emptyCard.children[1])).toBe(true)
    expect(hasOwnFallback(filledCard.children[1])).toBe(false)
  })
})

// =============================================================================
// Next-target resolution
// =============================================================================

describe('ChoiceBranch next-target resolution', () => {
  it('renders a sub-event for a nextEventId that names one', () => {
    const { container } = render(<ChoiceBranch {...baseProps()} linkedSubEventId="901301" />)

    expect(container.textContent).toContain('Sub event desc SINNER')
    expect(subChoiceBody(container, 'Sub 5').textContent).toContain('Deep sub event desc')
  })

  it('prefers the sub-event own selection events over the ones in outer scope', () => {
    const { container } = render(<ChoiceBranch {...baseProps()} linkedSubEventId="901301" />)
    const body = subChoiceBody(container, 'Sub 3')

    expect(body.textContent).toContain('LOCAL-3')
    expect(body.textContent).not.toContain('PARENT-3')
    expect(coinTossThresholds(body)).toEqual(['19+'])
  })

  it('resolves a coin toss that only the sub-event knows about', () => {
    const { container } = render(<ChoiceBranch {...baseProps()} linkedSubEventId="901301" />)

    expect(coinTossThresholds(subChoiceBody(container, 'Sub 4'))).toEqual(['7+'])
  })

  it('renders nothing for a nextEventId that names no sub-event and no coin toss', () => {
    const { container } = render(<ChoiceBranch {...baseProps()} linkedSubEventId="901301" />)

    expect(divsWithClass(subChoiceBody(container, 'Sub 6'), CARD)).toHaveLength(0)
  })

  it('renders nothing for a nextEventId whose selection event has no judgement', () => {
    const { container } = render(<ChoiceBranch {...baseProps()} linkedSubEventId="901301" />)

    expect(divsWithClass(subChoiceBody(container, 'Sub 7'), CARD)).toHaveLength(0)
  })

  it('does not re-enter the sub-event a choice points back at', () => {
    const { container } = render(<ChoiceBranch {...baseProps()} linkedSubEventId="901301" />)

    expect(divsWithClass(subChoiceBody(container, 'Sub 2'), CARD)).toHaveLength(0)
    expect(container.textContent?.match(/Sub event desc/g)).toHaveLength(1)
  })

  it('chains a coin toss outcome into another coin toss and into a sub-event', () => {
    const { container } = render(
      <ChoiceBranch
        {...baseProps()}
        selectionEvent={SELECTION_EVENTS['4']}
        selectionText={SELECTION_TEXTS['4']}
      />,
    )
    const [success, failure] = coinTossOutcomes(container)
    if (!success || !failure) throw new Error('expected a success and a failure outcome')

    const [successNext] = childrenWithClass(success, NEXT_EVENT_WRAPPER)
    if (!successNext) throw new Error('expected a next-event block under the success outcome')
    expect(coinTossThresholds(successNext)).toEqual(['15+'])

    const [failureNext] = childrenWithClass(failure, NEXT_EVENT_WRAPPER)
    if (!failureNext) throw new Error('expected a next-event block under the failure outcome')
    expect(failureNext.textContent).toContain('Sub event desc')
  })

  it('drops the outcome block for an unresolvable next id and for sub-results', () => {
    const { container } = render(
      <ChoiceBranch
        {...baseProps()}
        selectionEvent={SELECTION_EVENTS['6']}
        selectionText={SELECTION_TEXTS['6']}
      />,
    )
    const [success, failure] = coinTossOutcomes(container)
    if (!success || !failure) throw new Error('expected a success and a failure outcome')

    expect(childrenWithClass(success, NEXT_EVENT_WRAPPER)).toHaveLength(0)
    expect(childrenWithClass(failure, NEXT_EVENT_WRAPPER)).toHaveLength(0)
    expect(failure.textContent).toContain('Deep sub event desc')
  })

  it('keeps the coin toss header intact when reached through a next id', () => {
    const { container } = render(<ChoiceBranch {...baseProps()} linkedSubEventId="901301" />)
    const body = subChoiceBody(container, 'Sub 3')

    expect(body.textContent).toContain('Wrath')
    expect(body.textContent).toContain('Envy')
    expect(body.textContent).toContain('SUCCESS')
  })
})

// =============================================================================
// Branch labels
// =============================================================================

describe('ChoiceBranch branch labels', () => {
  it('labels coin toss sub-results by probability, then condition, then not at all', () => {
    const { container } = render(
      <ChoiceBranch
        {...baseProps()}
        selectionEvent={SELECTION_EVENTS['3']}
        selectionText={SELECTION_TEXTS['3']}
      />,
    )

    expect(branchLabels(container).slice(0, 3)).toEqual([
      '50%',
      'abEvent.condProb:{"value":25}',
      null,
    ])
  })

  it('labels narrative branches from probability and conditional results', () => {
    const { container } = render(
      <ChoiceBranch
        {...baseProps()}
        choice={{
          index: 0,
          conditionalResults: [
            { condition: 'MpAverage_Under30', effects: [{ effect: 'CR' }] },
            { effects: [{ effect: 'CR2' }] },
          ],
        }}
        option={{ message: 'Check', result: ['cond text', 'plain text'] }}
      />,
    )

    expect(branchLabels(container)).toEqual(['abEvent.condMpAverageUnder:{"value":"30"}', null])
  })

  it('labels probability results rendered without narrative text', () => {
    const { container } = render(
      <ChoiceBranch
        {...baseProps()}
        choice={{
          index: 0,
          probabilityResults: [
            { probability: 0.25, effects: [{ effect: 'PR' }] },
            { probability: 0.75, effects: [{ effect: 'PR2' }] },
          ],
        }}
      />,
    )

    expect(branchLabels(container)).toEqual(['25%', '75%'])
  })
})

// =============================================================================
// Whole-tree regression net over the same matrix the refactor was verified on
// =============================================================================

describe('ChoiceBranch rendered tree', () => {
  for (const testCase of STRUCTURAL_CASES) {
    it(`renders a stable tree: ${testCase.name}`, () => {
      const { container } = render(<ChoiceBranch {...testCase.props} />)

      expect(container.innerHTML).toMatchSnapshot()
    })
  }
})
