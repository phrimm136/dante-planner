/**
 * FilteredEntityGrid.test.tsx
 *
 * Covers the grid's own wiring: which slots hide, how search terms reach the predicate,
 * the term-less variant the abnormality event list uses, and the two layout switches.
 */

import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'

import { FilteredEntityGrid } from '../FilteredEntityGrid'
import { createTestFilterStore } from '@/test-utils/filterStore'

interface Item {
  id: string
  keyword: string
}

interface State {
  selectedKeywords: ReadonlySet<string>
}

const ITEMS: Item[] = [
  { id: 'a', keyword: 'Burst' },
  { id: 'b', keyword: 'Charge' },
  { id: 'c', keyword: 'Burst' },
]

function matches(
  item: Item,
  state: { values: State; searchQuery: string },
  terms: readonly string[],
): boolean {
  const { selectedKeywords } = state.values
  if (selectedKeywords.size > 0 && !selectedKeywords.has(item.keyword)) return false
  if (!state.searchQuery) return true
  return terms.includes(state.searchQuery)
}

function renderGrid(
  values: Partial<State> = {},
  searchQuery = '',
  overrides: Partial<Parameters<typeof FilteredEntityGrid<Item, State>>[0]> = {},
) {
  const store = createTestFilterStore<State>(
    { selectedKeywords: new Set(), ...values },
    searchQuery,
  )

  return render(
    <FilteredEntityGrid
      items={ITEMS}
      getKey={(item) => item.id}
      store={store}
      matches={matches}
      renderCard={(item) => <a data-testid={item.id}>{item.id}</a>}
      emptyStateKey="test.emptyState"
      emptyStateFallback="Nothing matches."
      cardWidth={100}
      cardHeight={200}
      mobileScale={0.8}
      {...overrides}
    />,
  )
}

describe('FilteredEntityGrid', () => {
  it('renders one card per item', () => {
    renderGrid()

    expect(screen.getByTestId('a')).toBeInTheDocument()
    expect(screen.getByTestId('b')).toBeInTheDocument()
    expect(screen.getByTestId('c')).toBeInTheDocument()
  })

  it('hides the slots whose item does not match', () => {
    const { container } = renderGrid({ selectedKeywords: new Set(['Burst']) })

    expect(container.querySelectorAll('div.hidden')).toHaveLength(1)
    expect(container.querySelector('div.hidden')).toContainElement(screen.getByTestId('b'))
  })

  it('shows the empty state only when nothing matches', () => {
    renderGrid({ selectedKeywords: new Set(['Poise']) })

    expect(screen.getByText('Nothing matches.')).toBeInTheDocument()
  })

  it('leaves the empty state out while something matches', () => {
    renderGrid({ selectedKeywords: new Set(['Charge']) })

    expect(screen.queryByText('Nothing matches.')).not.toBeInTheDocument()
  })

  it('feeds each slot the terms its item built', () => {
    const { container } = renderGrid({}, 'burst-term', {
      buildTerms: (item) => (item.keyword === 'Burst' ? ['burst-term'] : []),
    })

    expect(container.querySelectorAll('div.hidden')).toHaveLength(1)
    expect(container.querySelector('div.hidden')).toContainElement(screen.getByTestId('b'))
  })

  it('treats a grid with no term builder as having no terms', () => {
    renderGrid({}, 'anything')

    expect(screen.getByText('Nothing matches.')).toBeInTheDocument()
  })

  it('leaves the grid unwrapped by default', () => {
    const { container } = renderGrid()

    expect(container.querySelector('div.pt-4')).toBeNull()
  })

  it('wraps the grid in the class it is given', () => {
    const { container } = renderGrid({}, '', { gridWrapperClassName: 'pt-4' })

    expect(container.querySelector('div.pt-4 > div.grid')).not.toBeNull()
  })

  it('pins row height only when asked', () => {
    const { container: loose } = renderGrid()
    expect(loose.querySelector('div.grid')).not.toHaveStyle({ gridAutoRows: '200px' })

    const { container: pinned } = renderGrid({}, '', { fixedRowHeight: true })
    expect(pinned.querySelector('div.grid')).toHaveStyle({ gridAutoRows: '200px' })
  })
})
