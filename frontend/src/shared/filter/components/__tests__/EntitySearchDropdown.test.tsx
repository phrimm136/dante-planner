import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'

import { EntitySearchDropdown } from '../EntitySearchDropdown'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: { ns?: string; defaultValue?: string }) =>
      opts?.ns === 'sinnerNames' ? `sinner:${key}` : key,
  }),
}))

vi.mock('@/shared/gameData', () => ({
  getSinnerFromId: (id: string) => `sinner-${id.slice(0, 3)}`,
}))

vi.mock('../SearchableMultiSelect', () => ({
  SearchableMultiSelect: ({
    options,
    placeholder,
    searchPlaceholder,
    selectedValues,
  }: {
    options: { value: string; label: string }[]
    placeholder: string
    searchPlaceholder: string
    selectedValues: Set<string>
  }) => (
    <div>
      <span data-testid="placeholder">{placeholder}</span>
      <span data-testid="search-placeholder">{searchPlaceholder}</span>
      <span data-testid="selected-count">{selectedValues.size}</span>
      <ul>
        {options.map((option) => (
          <li key={option.value}>{option.label}</li>
        ))}
      </ul>
    </div>
  ),
}))

const ids = ['10101', '10102']
const names = { '10101': 'LCB Sinner\nYi Sang' }

describe('EntitySearchDropdown', () => {
  it('labels options "<name> - <sinner>" with newlines flattened, falling back to the id', () => {
    render(
      <EntitySearchDropdown
        selected={new Set()}
        onSelectionChange={vi.fn()}
        ids={ids}
        names={names}
        placeholderKey="keyword.filterIdentity"
      />,
    )

    expect(screen.getByText('LCB Sinner Yi Sang - sinner:sinner-101')).toBeInTheDocument()
    expect(screen.getByText('10102 - sinner:sinner-101')).toBeInTheDocument()
  })

  it('takes its placeholder from the injected key and shares the search placeholder', () => {
    render(
      <EntitySearchDropdown
        selected={new Set(['10101'])}
        onSelectionChange={vi.fn()}
        ids={ids}
        names={names}
        placeholderKey="keyword.filterEgo"
      />,
    )

    expect(screen.getByTestId('placeholder')).toHaveTextContent('keyword.filterEgo')
    expect(screen.getByTestId('search-placeholder')).toHaveTextContent('keyword.searchPlaceholder')
    expect(screen.getByTestId('selected-count')).toHaveTextContent('1')
  })
})
