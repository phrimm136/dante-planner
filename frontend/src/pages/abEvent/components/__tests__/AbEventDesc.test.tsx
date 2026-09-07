import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AbEventDesc } from '../AbEventDesc'

let language = 'EN'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language } }),
}))

vi.mock('../../hooks/useAbEventListData', () => ({
  useAbEventListI18n: () => ({ '901001': 'An empty room of cement.' }),
}))

vi.mock('@/components/ui/KoreanText', () => ({
  KoreanText: ({ children }: { children: React.ReactNode }) => (
    <span data-testid="korean-text">{children}</span>
  ),
}))

describe('AbEventDesc', () => {
  it('renders the description for the id', () => {
    render(<AbEventDesc id="901001" />)

    expect(screen.getByText('An empty room of cement.')).toBeInTheDocument()
    expect(screen.queryByTestId('korean-text')).toBeNull()
  })

  it('renders nothing for an id the language has no description for', () => {
    const { container } = render(<AbEventDesc id="999999" />)

    expect(container.textContent).toBe('')
  })

  it('routes Korean through KoreanText', () => {
    language = 'KR'

    render(<AbEventDesc id="901001" />)

    expect(screen.getByTestId('korean-text')).toHaveTextContent('An empty room of cement.')
  })
})
