import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'

import jpTable from '@static/data/fontAdvances/JP.json'
import krTable from '@static/data/fontAdvances/KR.json'
import { FontAdvanceTableSchema } from '@/shared/cardLayout'
import { identityNameLines, nameLinePitch } from '../../lib/cardLayout'
import { IdentityName } from '../IdentityName'
import { asIdentityId } from '@/test-utils/fixtures'

/** The faces the cards are drawn in, as the site ships them. */
const KR_TABLE = FontAdvanceTableSchema.parse(krTable)
const JP_TABLE = FontAdvanceTableSchema.parse(jpTable)

const language = { current: 'KR' }

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: language.current } }),
}))

vi.mock('@/shared/cardLayout', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/cardLayout')>()),
  useFontAdvances: () => (language.current === 'JP' ? JP_TABLE : KR_TABLE),
}))

vi.mock('../../hooks/useIdentityListData', () => ({
  useIdentityListI18n: () => ({
    '10101': 'The Blue - Reverberation',
    '10201': 'Plain Name',
    '10110': 'ロボトミーE.G.O::\n厳粛な哀悼',
  }),
}))

describe('IdentityName', () => {
  it('falls back to the id when the language pack has no name', () => {
    language.current = 'KR'
    render(<IdentityName id={asIdentityId('10999')} />)

    expect(screen.getByText('10999')).toBeInTheDocument()
  })

  it('draws the name in the language display face at that language’s line pitch', () => {
    language.current = 'KR'
    const { container } = render(<IdentityName id={asIdentityId('10201')} />)
    const name = container.querySelector('.text-identity-name') as HTMLElement

    expect(name.style.fontFamily).toBe('var(--font-kotra)')
    expect(Number(name.style.lineHeight)).toBeCloseTo(nameLinePitch(KR_TABLE, 'KR'), 6)
  })

  it('draws one span per line the advance table breaks the name into', () => {
    language.current = 'JP'
    const { container } = render(<IdentityName id={asIdentityId('10110')} />)
    const lines = [...container.querySelectorAll('[data-testid="identity-name-line"]')]

    expect(lines.map((line) => line.textContent)).toEqual(
      identityNameLines('ロボトミーE.G.O::\n厳粛な哀悼', JP_TABLE),
    )
    expect(lines.map((line) => line.textContent)).toEqual(['ロボトミーE.G.O::', '厳粛な哀悼'])
  })

  it('leaves no line for the browser to wrap', () => {
    language.current = 'JP'
    const { container } = render(<IdentityName id={asIdentityId('10110')} />)

    for (const line of container.querySelectorAll('[data-testid="identity-name-line"]')) {
      expect((line as HTMLElement).style.whiteSpace).toBe('nowrap')
    }
  })

  it('breaks a Latin name where the advance table breaks it, not where the browser would', () => {
    language.current = 'KR'
    const { container } = render(<IdentityName id={asIdentityId('10101')} />)
    const lines = [...container.querySelectorAll('[data-testid="identity-name-line"]')]

    expect(lines.map((line) => line.textContent)).toEqual(
      identityNameLines('The Blue - Reverberation', KR_TABLE),
    )
  })
})
