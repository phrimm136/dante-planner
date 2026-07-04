/**
 * PassiveFlavor.test.tsx
 *
 * Verifies the eager-prop branch of PassiveCard renders the optional
 * `flavor` lore line. The granular-Suspense branch (`PassiveCardWithSuspense`)
 * fetches from `useIdentityDetailI18n` at runtime and is exercised by the
 * integration tests in the route layer.
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PassiveCard } from '../PassiveI18n'
import { FLAVOR_TEXT_COLOR } from '@/shared/gameData'

// FormattedDescription pulls a keyword pipeline that needs query providers.
vi.mock('@/shared/gameText/components/FormattedDescription', () => ({
  FormattedDescription: ({ text }: { text: string }) => <span>{text}</span>,
}))

// StyledSkillName is unrelated to flavor; stub it.
vi.mock('@/shared/gameText/components/StyledName', () => ({
  StyledSkillName: ({ name }: { name: string }) => <span>{name}</span>,
  StyledNameSkeleton: () => <span data-testid="styled-name-skeleton" />,
}))

vi.mock('@/shared/assets', () => ({
  getAffinityIconPath: (a: string) => `/icons/${a}.webp`,
  getIdentityPassiveCountIconPath: () => '/icons/count.webp',
  getLockIconPath: () => '/icons/lock.webp',
}))

vi.mock(import('react-i18next'), async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useTranslation: () =>
      ({
        t: (key: string) => key,
        i18n: { language: 'EN' },
      }) as unknown as ReturnType<typeof actual.useTranslation>,
  }
})

describe('PassiveCard flavor rendering', () => {
  const baseProps = {
    name: 'Blade of the House of Spiders',
    desc: 'Transfer HP damage taken to the lowest-HP ally.',
    isLocked: false,
  }

  it('renders the flavor lore line when provided', () => {
    render(
      <PassiveCard {...baseProps} flavor="'The things I dread seeing again always come back.'" />,
    )

    const flavor = screen.getByTestId('passive-flavor')
    expect(flavor).toBeInTheDocument()
    expect(flavor).toHaveTextContent("'The things I dread seeing again always come back.'")
    expect(flavor).toHaveStyle({ color: FLAVOR_TEXT_COLOR })
  })

  it('omits the flavor element when flavor prop is missing', () => {
    render(<PassiveCard {...baseProps} />)
    expect(screen.queryByTestId('passive-flavor')).not.toBeInTheDocument()
  })

  it('omits the flavor element when flavor prop is empty', () => {
    render(<PassiveCard {...baseProps} flavor="" />)
    expect(screen.queryByTestId('passive-flavor')).not.toBeInTheDocument()
  })

  it('renders flavor after the description in DOM order', () => {
    const { container } = render(
      <PassiveCard {...baseProps} desc="Mechanical passive desc." flavor="Lore tail." />,
    )

    const root = container.firstChild as HTMLElement
    const desc = screen.getByText('Mechanical passive desc.')
    const flavor = screen.getByTestId('passive-flavor')

    const children = Array.from(root.children)
    const descIdx = children.findIndex((c) => c.contains(desc))
    const flavorIdx = children.indexOf(flavor)
    expect(flavorIdx).toBeGreaterThan(descIdx)
  })

  it('preserves multi-line flavor via whitespace-pre-line', () => {
    const multiLine = 'Line one.\nLine two.'
    render(<PassiveCard {...baseProps} flavor={multiLine} />)
    const flavor = screen.getByTestId('passive-flavor')
    expect(flavor).toHaveClass('whitespace-pre-line')
    expect(flavor.textContent).toBe(multiLine)
  })
})
