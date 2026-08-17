import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { UnitKeywordDropdown } from '../UnitKeywordDropdown'
import { ASSOCIATIONS } from '@/shared/gameData'

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
vi.mock('@/shared/filter/hooks/useFilterI18nData', () => ({
  useFilterI18nData: () => ({
    seasonsI18n: {},
    unitKeywordsI18n: Object.fromEntries(ASSOCIATIONS.map((a) => [a, `Label_${a}`])),
  }),
}))

// Sorted by label to match SearchableMultiSelect's localeCompare sort
const sorted = [...ASSOCIATIONS].sort((a, b) => `Label_${a}`.localeCompare(`Label_${b}`))
const [firstAssoc, secondAssoc] = sorted
if (firstAssoc === undefined || secondAssoc === undefined) {
  throw new Error('ASSOCIATIONS carries fewer than the two keywords these tests select')
}

describe('UnitKeywordDropdown', () => {
  const defaultProps = {
    selected: new Set<string>(),
    onSelectionChange: vi.fn(),
  }

  it('renders combobox trigger with label text', () => {
    render(<UnitKeywordDropdown {...defaultProps} />)

    expect(screen.getByRole('combobox')).toBeInTheDocument()
    expect(screen.getByText('Unit Keywords')).toBeInTheDocument()
  })

  it('shows selected count when unit keywords are active', () => {
    const selected = new Set([firstAssoc, secondAssoc])

    render(<UnitKeywordDropdown {...defaultProps} selected={selected} />)

    expect(screen.getByText('(2)')).toBeInTheDocument()
  })

  it('does not show count when no unit keywords selected', () => {
    render(<UnitKeywordDropdown {...defaultProps} />)

    expect(screen.queryByText(/\(\d+\)/)).toBeNull()
  })

  it('displays options when opened', async () => {
    const user = userEvent.setup()
    render(<UnitKeywordDropdown {...defaultProps} />)

    await user.click(screen.getByRole('combobox'))

    expect(
      screen.getByRole('option', { name: new RegExp(`Label_${firstAssoc}`) }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('option', { name: new RegExp(`Label_${secondAssoc}`) }),
    ).toBeInTheDocument()
  })

  it('calls onSelectionChange with added keyword on click', async () => {
    const onSelectionChange = vi.fn()
    const user = userEvent.setup()

    render(
      <UnitKeywordDropdown selected={new Set<string>()} onSelectionChange={onSelectionChange} />,
    )

    await user.click(screen.getByRole('combobox'))
    await user.click(screen.getByRole('option', { name: new RegExp(`Label_${firstAssoc}`) }))

    expect(onSelectionChange).toHaveBeenCalledWith(new Set([firstAssoc]))
  })

  it('calls onSelectionChange with removed keyword when toggling off', async () => {
    const onSelectionChange = vi.fn()
    const user = userEvent.setup()

    render(
      <UnitKeywordDropdown
        selected={new Set([firstAssoc])}
        onSelectionChange={onSelectionChange}
      />,
    )

    await user.click(screen.getByRole('combobox'))
    await user.click(screen.getByRole('option', { name: new RegExp(`Label_${firstAssoc}`) }))

    expect(onSelectionChange).toHaveBeenCalledWith(new Set())
  })

  it('keeps popover open after selecting an item', async () => {
    const user = userEvent.setup()
    render(<UnitKeywordDropdown {...defaultProps} />)

    await user.click(screen.getByRole('combobox'))
    await user.click(screen.getByRole('option', { name: new RegExp(`Label_${firstAssoc}`) }))

    expect(
      screen.getByRole('option', { name: new RegExp(`Label_${secondAssoc}`) }),
    ).toBeInTheDocument()
  })

  it('displays element counts when provided', async () => {
    const user = userEvent.setup()
    const counts = { [firstAssoc]: 15 }

    render(<UnitKeywordDropdown {...defaultProps} counts={counts} />)

    await user.click(screen.getByRole('combobox'))

    expect(screen.getByText('15')).toBeInTheDocument()
  })
})
