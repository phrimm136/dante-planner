import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { UnitKeywordDropdown } from '../UnitKeywordDropdown'
import { ASSOCIATIONS } from '@/lib/constants'

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
    seasonsI18n: {},
    unitKeywordsI18n: Object.fromEntries(
      ASSOCIATIONS.map((a) => [a, `Label_${a}`])
    ),
  }),
}))

// Sorted by label (Label_KEY) to match component's localeCompare sort
const sorted = [...ASSOCIATIONS].sort((a, b) =>
  `Label_${a}`.localeCompare(`Label_${b}`)
)

describe('UnitKeywordDropdown', () => {
  const defaultProps = {
    selectedUnitKeywords: new Set<string>(),
    onSelectionChange: vi.fn(),
  }

  it('renders trigger button with label', () => {
    render(<UnitKeywordDropdown {...defaultProps} />)

    expect(screen.getByRole('button', { name: /unit keywords/i })).toBeInTheDocument()
  })

  it('shows selected count when unit keywords are active', () => {
    const selected = new Set([sorted[0], sorted[1]])

    render(
      <UnitKeywordDropdown
        {...defaultProps}
        selectedUnitKeywords={selected}
      />
    )

    expect(screen.getByText('(2)')).toBeInTheDocument()
  })

  it('does not show count when no unit keywords selected', () => {
    render(<UnitKeywordDropdown {...defaultProps} />)

    expect(screen.queryByText(/\(\d+\)/)).toBeNull()
  })

  it('displays items when dropdown opens', async () => {
    const user = userEvent.setup()
    render(<UnitKeywordDropdown {...defaultProps} />)

    await user.click(screen.getByRole('button', { name: /unit keywords/i }))

    expect(screen.getByText(`Label_${sorted[0]}`)).toBeInTheDocument()
    expect(screen.getByText(`Label_${sorted[1]}`)).toBeInTheDocument()
  })

  it('calls onSelectionChange with added keyword on click', async () => {
    const onSelectionChange = vi.fn()
    const user = userEvent.setup()

    render(
      <UnitKeywordDropdown
        selectedUnitKeywords={new Set<string>()}
        onSelectionChange={onSelectionChange}
      />
    )

    await user.click(screen.getByRole('button', { name: /unit keywords/i }))
    await user.click(screen.getByText(`Label_${sorted[0]}`))

    expect(onSelectionChange).toHaveBeenCalledWith(new Set([sorted[0]]))
  })

  it('calls onSelectionChange with removed keyword when toggling off', async () => {
    const onSelectionChange = vi.fn()
    const user = userEvent.setup()
    const firstAssoc = sorted[0]

    render(
      <UnitKeywordDropdown
        selectedUnitKeywords={new Set([firstAssoc])}
        onSelectionChange={onSelectionChange}
      />
    )

    await user.click(screen.getByRole('button', { name: /unit keywords/i }))
    await user.click(screen.getByText(`Label_${firstAssoc}`))

    expect(onSelectionChange).toHaveBeenCalledWith(new Set())
  })

  it('keeps dropdown open after selecting an item', async () => {
    const user = userEvent.setup()
    render(<UnitKeywordDropdown {...defaultProps} />)

    await user.click(screen.getByRole('button', { name: /unit keywords/i }))
    await user.click(screen.getByText(`Label_${sorted[0]}`))

    // Dropdown should remain open - other items still visible
    expect(screen.getByText(`Label_${sorted[1]}`)).toBeInTheDocument()
  })

  it('progressively renders all association items', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    vi.useFakeTimers({ shouldAdvanceTime: true })
    render(<UnitKeywordDropdown {...defaultProps} />)

    await user.click(screen.getByRole('button', { name: /unit keywords/i }))

    // Wait for progressive rendering to complete
    await vi.waitFor(() => {
      const menuItems = screen.getAllByRole('menuitemcheckbox')
      expect(menuItems.length).toBe(ASSOCIATIONS.length)
    })

    vi.useRealTimers()
  })
})
