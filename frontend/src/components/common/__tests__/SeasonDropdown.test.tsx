import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SeasonDropdown } from '../SeasonDropdown'
import { SEASONS, type Season } from '@/lib/constants'

// Mock i18n - return key as translation, preserve initReactI18next for i18n setup
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, fallback?: string) => fallback ?? key,
      i18n: { language: 'EN' },
    }),
  }
})

// Mock filter i18n data hook to avoid Suspense/query setup
vi.mock('@/hooks/useFilterI18nData', () => ({
  useFilterI18nData: () => ({
    seasonsI18n: Object.fromEntries(
      SEASONS.map((s) => [String(s), `Season ${s}`])
    ),
    unitKeywordsI18n: {},
  }),
}))

describe('SeasonDropdown', () => {
  const defaultProps = {
    selectedSeasons: new Set<Season>(),
    onSelectionChange: vi.fn(),
  }

  it('renders trigger button with label', () => {
    render(<SeasonDropdown {...defaultProps} />)

    expect(screen.getByRole('button', { name: /season/i })).toBeInTheDocument()
  })

  it('shows selected count when seasons are active', () => {
    render(
      <SeasonDropdown
        {...defaultProps}
        selectedSeasons={new Set<Season>([1, 3])}
      />
    )

    expect(screen.getByText('(2)')).toBeInTheDocument()
  })

  it('does not show count when no seasons selected', () => {
    render(<SeasonDropdown {...defaultProps} />)

    expect(screen.queryByText(/\(\d+\)/)).toBeNull()
  })

  it('displays season items when opened', async () => {
    const user = userEvent.setup()
    render(<SeasonDropdown {...defaultProps} />)

    await user.click(screen.getByRole('button', { name: /season/i }))

    // Radix renders in portal - all seasons should be visible
    for (const season of SEASONS) {
      expect(screen.getByText(`Season ${season}`)).toBeInTheDocument()
    }
  })

  it('calls onSelectionChange with added season on click', async () => {
    const onSelectionChange = vi.fn()
    const user = userEvent.setup()

    render(
      <SeasonDropdown
        selectedSeasons={new Set<Season>()}
        onSelectionChange={onSelectionChange}
      />
    )

    await user.click(screen.getByRole('button', { name: /season/i }))
    await user.click(screen.getByText(`Season ${SEASONS[0]}`))

    expect(onSelectionChange).toHaveBeenCalledWith(new Set([SEASONS[0]]))
  })

  it('calls onSelectionChange with removed season when toggling off', async () => {
    const onSelectionChange = vi.fn()
    const user = userEvent.setup()
    const firstSeason = SEASONS[0]

    render(
      <SeasonDropdown
        selectedSeasons={new Set<Season>([firstSeason])}
        onSelectionChange={onSelectionChange}
      />
    )

    await user.click(screen.getByRole('button', { name: /season/i }))
    await user.click(screen.getByText(`Season ${firstSeason}`))

    expect(onSelectionChange).toHaveBeenCalledWith(new Set())
  })

  it('keeps dropdown open after selecting an item', async () => {
    const user = userEvent.setup()
    render(<SeasonDropdown {...defaultProps} />)

    await user.click(screen.getByRole('button', { name: /season/i }))
    await user.click(screen.getByText(`Season ${SEASONS[0]}`))

    // Dropdown should still be open - other items still visible
    expect(screen.getByText(`Season ${SEASONS[1]}`)).toBeInTheDocument()
  })
})
