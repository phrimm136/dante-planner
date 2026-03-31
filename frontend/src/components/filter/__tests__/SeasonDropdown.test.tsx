import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SeasonDropdown } from '../SeasonDropdown'
import { SEASONS, type Season } from '@/lib/constants'

// Mock i18n
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

  it('renders combobox trigger with label text', () => {
    render(<SeasonDropdown {...defaultProps} />)

    expect(screen.getByRole('combobox')).toBeInTheDocument()
    expect(screen.getByText('Season')).toBeInTheDocument()
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

  it('displays season options when opened', async () => {
    const user = userEvent.setup()
    render(<SeasonDropdown {...defaultProps} />)

    await user.click(screen.getByRole('combobox'))

    for (const season of SEASONS) {
      expect(screen.getByRole('option', { name: new RegExp(`Season ${season}`) })).toBeInTheDocument()
    }
  })

  it('preserves season ID order (not alphabetical)', async () => {
    const user = userEvent.setup()
    render(<SeasonDropdown {...defaultProps} />)

    await user.click(screen.getByRole('combobox'))

    const options = screen.getAllByRole('option')
    const labels = options.map((opt) => opt.textContent)

    for (let i = 0; i < SEASONS.length; i++) {
      expect(labels[i]).toContain(`Season ${SEASONS[i]}`)
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

    await user.click(screen.getByRole('combobox'))
    await user.click(screen.getByRole('option', { name: new RegExp(`Season ${SEASONS[0]}`) }))

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

    await user.click(screen.getByRole('combobox'))
    await user.click(screen.getByRole('option', { name: new RegExp(`Season ${firstSeason}`) }))

    expect(onSelectionChange).toHaveBeenCalledWith(new Set())
  })

  it('keeps popover open after selecting an item', async () => {
    const user = userEvent.setup()
    render(<SeasonDropdown {...defaultProps} />)

    await user.click(screen.getByRole('combobox'))
    await user.click(screen.getByRole('option', { name: new RegExp(`Season ${SEASONS[0]}`) }))

    expect(screen.getByRole('option', { name: new RegExp(`Season ${SEASONS[1]}`) })).toBeInTheDocument()
  })

  it('displays element counts when provided', async () => {
    const user = userEvent.setup()
    const counts = { [String(SEASONS[0])]: 42 }

    render(<SeasonDropdown {...defaultProps} counts={counts} />)

    await user.click(screen.getByRole('combobox'))

    expect(screen.getByText('42')).toBeInTheDocument()
  })
})
