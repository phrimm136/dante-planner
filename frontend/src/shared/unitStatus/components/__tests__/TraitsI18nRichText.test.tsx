import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'

import unitKeywordsEn from '@static/i18n/EN/unitKeywords.json'

import { TraitsI18n } from '../TraitsI18n'

vi.mock('@/shared/filter/hooks/useUnitKeywords', () => ({
  useUnitKeywords: vi.fn(),
}))

import { useUnitKeywords } from '@/shared/filter'

function badgeOf(label: string): HTMLElement {
  vi.mocked(useUnitKeywords).mockReturnValue({ TRAIT: label })
  const { container } = render(<TraitsI18n traits={['TRAIT']} />)
  return container.querySelector('span.px-2') as HTMLElement
}

function runs(badge: HTMLElement) {
  return [...badge.querySelectorAll<HTMLElement>('span')].map((span) => ({
    text: span.textContent,
    color: span.style.getPropertyValue('--rt-color'),
    decoration: span.style.textDecorationLine,
    fontSize: span.style.fontSize,
  }))
}

describe('TraitsI18n rich text', () => {
  it('shrinks the sized kana of a JP trait', () => {
    const badge = badgeOf('サ<size=50%>ル</size>党派')

    expect(badge.textContent).toBe('サル党派')
    expect(runs(badge)).toEqual([{ text: 'ル', color: '', decoration: '', fontSize: '50%' }])
  })

  it('strikes a coloured trait in its colour', () => {
    const badge = badgeOf('<color=#d40000><s>Jia Family</s></color>')

    expect(runs(badge)).toEqual([
      { text: 'Jia Family', color: '#d40000', decoration: 'line-through', fontSize: '' },
    ])
  })

  it('strikes the shipped Le Sette Famiglie label that repeats <s> where </s> belongs', () => {
    const badge = badgeOf(unitKeywordsEn.SEVENFAMILIES_CANCELED)

    expect(badge.textContent).toBe('Le Sette Famiglie')
    expect(runs(badge)).toEqual([
      { text: 'Le Sette Famiglie', color: '#d40000', decoration: 'line-through', fontSize: '' },
    ])
  })

  it('renders a named colour as its TextMeshPro value', () => {
    expect(runs(badgeOf('<color=red>x</color>'))).toEqual([
      { text: 'x', color: '#ff0000', decoration: '', fontSize: '' },
    ])
  })

  it('leaves a plain label as text', () => {
    expect(badgeOf('plain label').innerHTML).toBe('plain label')
  })
})
