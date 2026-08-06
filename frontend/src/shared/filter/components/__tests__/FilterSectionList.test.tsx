/**
 * FilterSectionList.test.tsx
 *
 * Covers what the sections table produces: the bound control, the active count taken
 * from the selection, and the loading fallback around the sections that suspend.
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { lazy } from 'react'

import { FilterSectionList, filterSection } from '../FilterSectionList'

function Control({
  selected,
  onSelectionChange,
}: {
  selected: Set<string>
  onSelectionChange: (next: Set<string>) => void
}) {
  return (
    <button
      data-testid="control"
      onClick={() => {
        onSelectionChange(new Set(['picked']))
      }}
    >
      {Array.from(selected).join(',')}
    </button>
  )
}

function CountedControl({
  selected,
  counts,
}: {
  selected: Set<string>
  onSelectionChange: (next: Set<string>) => void
  counts: Record<string, number>
}) {
  return <span data-testid="counted">{`${String(selected.size)}:${JSON.stringify(counts)}`}</span>
}

describe('filterSection', () => {
  it('takes the active count from the selection', () => {
    const entry = filterSection({
      key: 'selectedSinners',
      titleKey: 'filters.sinner',
      Component: Control,
      selected: new Set(['a', 'b']),
      onSelectionChange: vi.fn(),
    })

    expect(entry.activeCount).toBe(2)
  })

  it('binds the selection into the control', () => {
    const entry = filterSection({
      key: 'selectedSinners',
      titleKey: 'filters.sinner',
      Component: Control,
      selected: new Set(['Gregor']),
      onSelectionChange: vi.fn(),
    })

    render(<FilterSectionList sections={[entry]} />)

    expect(screen.getByTestId('control')).toHaveTextContent('Gregor')
  })

  it('forwards the extra props the control needs', () => {
    const entry = filterSection({
      key: 'selectedSeasons',
      titleKey: 'filters.season',
      Component: CountedControl,
      selected: new Set(['1']),
      onSelectionChange: vi.fn(),
      props: { counts: { '1': 7 } },
    })

    render(<FilterSectionList sections={[entry]} />)

    expect(screen.getByTestId('counted')).toHaveTextContent('1:{"1":7}')
  })
})

describe('FilterSectionList', () => {
  it('renders the title with its fallback', () => {
    const entry = filterSection({
      key: 'selectedSinners',
      titleKey: 'filters.thisKeyDoesNotExist',
      titleFallback: 'Sinner',
      Component: Control,
      selected: new Set<string>(),
      onSelectionChange: vi.fn(),
    })

    render(<FilterSectionList sections={[entry]} />)

    expect(screen.getByText('Sinner')).toBeInTheDocument()
  })

  it('shows the active count beside the title', () => {
    const entry = filterSection({
      key: 'selectedSinners',
      titleKey: 'filters.thisKeyDoesNotExist',
      titleFallback: 'Sinner',
      Component: Control,
      selected: new Set(['a', 'b', 'c']),
      onSelectionChange: vi.fn(),
    })

    render(<FilterSectionList sections={[entry]} />)

    expect(screen.getByText('(3)')).toBeInTheDocument()
  })

  it('renders every section in table order', () => {
    const sections = ['first', 'second', 'third'].map((title) =>
      filterSection({
        key: title,
        titleKey: 'filters.thisKeyDoesNotExist',
        titleFallback: title,
        Component: Control,
        selected: new Set<string>(),
        onSelectionChange: vi.fn(),
      }),
    )

    render(<FilterSectionList sections={sections} />)

    expect(screen.getAllByTestId('control')).toHaveLength(3)
    expect(screen.getByText('first')).toBeInTheDocument()
    expect(screen.getByText('third')).toBeInTheDocument()
  })

  it('holds a suspending control behind the loading fallback', async () => {
    let resolveControl: () => void = () => {}
    const pending = new Promise<void>((resolve) => {
      resolveControl = resolve
    })
    const Lazy = lazy(async () => {
      await pending
      return { default: () => <span data-testid="lazy-control">loaded</span> }
    })

    const entry = filterSection({
      key: 'selectedThemePacks',
      titleKey: 'filters.thisKeyDoesNotExist',
      titleFallback: 'Theme Pack',
      suspense: true,
      Component: Lazy as unknown as typeof Control,
      selected: new Set<string>(),
      onSelectionChange: vi.fn(),
    })

    render(<FilterSectionList sections={[entry]} />)

    expect(screen.queryByTestId('lazy-control')).not.toBeInTheDocument()

    resolveControl()
    await waitFor(() => {
      expect(screen.getByTestId('lazy-control')).toBeInTheDocument()
    })
  })
})
