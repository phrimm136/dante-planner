import { describe, expect, it, vi } from 'vitest'
import { render } from '@testing-library/react'

import { FormattedDescription } from '../FormattedDescription'
import type { ParsedSegment, ResolvedKeyword } from '../../types/KeywordTypes'

const SINKING: ResolvedKeyword = {
  type: 'battleKeyword',
  key: 'Sinking',
  displayText: 'Sinking',
  description: 'Lose HP',
  iconId: 'Sinking',
  color: '#f8c200',
}

vi.mock('@/shared/assets', () => ({
  getBattleKeywordIconPath: (path: string) => `/icons/${path}.webp`,
}))

vi.mock('@/shared/gameText/hooks/useKeywordFormatter', () => ({
  useKeywordFormatter: () => ({
    format: (text: string): ParsedSegment[] =>
      text.split(/(\[Sinking\])/).flatMap<ParsedSegment>((part) => {
        if (part === '') return []
        if (part === '[Sinking]') return [{ type: 'keyword', content: 'Sinking', keyword: SINKING }]
        return [{ type: 'text', content: part }]
      }),
  }),
}))

function renderText(text: string): HTMLElement {
  return render(<FormattedDescription text={text} />).container
}

function runs(container: HTMLElement): HTMLElement[] {
  const root = container.firstElementChild
  return root
    ? [...root.children].filter((child): child is HTMLElement => child.tagName === 'SPAN')
    : []
}

function runWithText(container: HTMLElement, text: string): HTMLElement | undefined {
  return runs(container).find((span) => span.textContent === text)
}

describe('FormattedDescription tag state', () => {
  it('draws an OrangeMark run as per-tag properties around its keyword chip', () => {
    const container = renderText(
      'On Clash Lose, <color=#ff6000><mark=#ff000040><b><u>gain [Sinking] 2</u></b></mark></color> next',
    )

    const marked = runs(container)
    expect(marked).toHaveLength(1)
    const [run] = marked
    expect(run?.textContent).toBe('gain Sinking 2')
    expect(run?.style.getPropertyValue('--rt-color')).toBe('#ff6000')
    expect(run?.style.getPropertyValue('--rt-mark-color')).toBe('#ff0000')
    expect(run?.style.getPropertyValue('--rt-mark-alpha')).toBe('25.098%')
    expect(run?.classList.contains('rt-ink')).toBe(true)
    expect(run?.classList.contains('rt-mark')).toBe(true)
    expect(run?.style.fontWeight).toBe('bold')
    expect(run?.style.textDecorationLine).toBe('underline')
    expect(run?.style.getPropertyValue('--rt-decoration-color')).toBe('#ff6000')

    const chip = run?.querySelector<HTMLElement>('button')
    expect(chip?.classList.contains('rt-ink')).toBe(true)
    expect(chip?.style.getPropertyValue('--rt-color')).toBe(SINKING.color)
    expect(container.textContent).toBe('On Clash Lose, gain Sinking 2 next')
  })

  it('renders a run missing its <u> bold without underline', () => {
    const container = renderText('<color=#ff6000><mark=#ff000040><b>X</u></b></mark></color>')

    const run = runWithText(container, 'X')
    expect(run?.style.fontWeight).toBe('bold')
    expect(run?.style.textDecorationLine).toBe('')
    expect(container.textContent).not.toContain('<')
  })

  it('keeps a doubled <u> underlining to its last close', () => {
    const container = renderText('<u><u>A</u>B</u>C')

    expect(runWithText(container, 'AB')?.style.textDecorationLine).toBe('underline')
    expect(runs(container)).toHaveLength(1)
    expect(container.textContent).toBe('ABC')
  })

  it('darkens struck text under a black mark without giving it a colour', () => {
    const container = renderText(
      '<mark=#000000AA>- <color=#ff9000><s></color>T<color=#ff0000></s></color></mark>',
    )

    const run = runWithText(container, 'T')
    expect(run?.style.getPropertyValue('--rt-mark-color')).toBe('#000000')
    expect(run?.style.getPropertyValue('--rt-mark-alpha')).toBe('66.667%')
    expect(run?.style.textDecorationLine).toBe('line-through')
    expect(run?.style.getPropertyValue('--rt-decoration-color')).toBe('#ff9000')
    expect(run?.style.getPropertyValue('--rt-color')).toBe('')
  })

  it('carries state across a line break', () => {
    const container = renderText('<b>a\nb</b>')

    const bold = runs(container)
    expect(bold.map((span) => [span.textContent, span.style.fontWeight])).toEqual([
      ['a', 'bold'],
      ['b', 'bold'],
    ])
    expect(container.querySelectorAll('br')).toHaveLength(1)
    expect(bold[0]?.nextElementSibling?.tagName).toBe('BR')
  })

  it('renders <color=red> red', () => {
    const container = renderText('<color=red>x</color>')

    expect(runWithText(container, 'x')?.style.getPropertyValue('--rt-color')).toBe('#ff0000')
  })

  it('renders an empty colour and an unknown tag as literal text', () => {
    expect(renderText('<color=>x</color>').textContent).toBe('<color=>x')
    expect(renderText('<Bloodfiend> gains').textContent).toBe('<Bloodfiend> gains')
  })

  it('colours upgradeHighlight text and leaves its keyword chip its own colour', () => {
    const container = renderText('<style="upgradeHighlight">a [Sinking] b</style> tail')

    const [run] = runs(container)
    expect(run?.style.getPropertyValue('--rt-color')).toBe('#f8c200')
    expect(run?.querySelector('button')?.style.getPropertyValue('--rt-color')).toBe(SINKING.color)
    expect(container.textContent).toBe('a Sinking b tail')
  })

  it('renders a legacy <style="OrangeMark"> run as plain text', () => {
    const container = renderText('<style="OrangeMark">x</style>')

    expect(container.textContent).toBe('x')
    expect(container.querySelector('.rt-mark')).toBeNull()
  })
})
