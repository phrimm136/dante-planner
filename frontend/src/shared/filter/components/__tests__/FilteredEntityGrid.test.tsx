/**
 * FilteredEntityGrid.test.tsx
 *
 * Covers the grid's own wiring: which slots hide, how search terms reach the predicate,
 * the term-less variant the abnormality event list uses, and the two layout switches.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, act } from '@testing-library/react'

import { PROGRESSIVE_REVEAL } from '@/lib/constants'

import type { CardGeometry } from '@/shared/cardLayout'
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

const MANY_ITEMS: Item[] = Array.from({ length: 25 }, (_, index) => ({
  id: `item-${String(index)}`,
  keyword: 'Burst',
}))

const GEOMETRY: CardGeometry = {
  size: { widthPx: 100, heightPx: 200 },
  mobileScale: 0.8,
  rows: 'slot',
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

  const result = render(
    <FilteredEntityGrid
      items={ITEMS}
      getKey={(item) => item.id}
      store={store}
      matches={matches}
      renderCard={(item) => <span data-testid={item.id}>{item.id}</span>}
      emptyStateKey="test.emptyState"
      emptyStateFallback="Nothing matches."
      geometry={GEOMETRY}
      {...overrides}
    />,
  )

  // The reveal window opens on the first frame; before it, every slot is empty.
  act(() => {
    vi.advanceTimersToNextFrame()
  })

  return result
}

describe('FilteredEntityGrid', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

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

  it('leaves rows to their content when the geometry asks for auto rows', () => {
    const { container } = renderGrid({}, '', {
      geometry: { ...GEOMETRY, rows: 'content' },
    })

    expect((container.querySelector('div.grid') as HTMLElement).style.gridAutoRows).toBe('')
  })

  it('pins rows to the column width over the card box it is given', () => {
    const { container } = renderGrid()

    expect(container.querySelector('div.grid')).toHaveStyle({ gridAutoRows: '200px' })
  })

  it('sizes every slot from the same card box', () => {
    const { container } = renderGrid()

    expect(container.querySelector('div.grid > div')).toHaveStyle({
      width: '100px',
      aspectRatio: '0.5',
    })
  })
})

describe('FilteredEntityGrid reveal window', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  function renderManyItems() {
    const store = createTestFilterStore<State>({ selectedKeywords: new Set() })

    return render(
      <FilteredEntityGrid
        items={MANY_ITEMS}
        getKey={(item) => item.id}
        store={store}
        matches={matches}
        renderCard={(item) => <span data-testid={item.id}>{item.id}</span>}
        emptyStateKey="test.emptyState"
        emptyStateFallback="Nothing matches."
        geometry={GEOMETRY}
      />,
    )
  }

  it('reserves every item a slot on the first commit', () => {
    const { container } = renderManyItems()

    expect(container.querySelectorAll('div.grid > div')).toHaveLength(MANY_ITEMS.length)
  })

  it('holds every card back until the window opens', () => {
    renderManyItems()

    expect(screen.queryAllByText(/^item-/)).toHaveLength(0)
  })

  it('fills the first batch on the frame the window opens', () => {
    renderManyItems()

    act(() => {
      vi.advanceTimersToNextFrame()
    })

    expect(screen.queryAllByText(/^item-/)).toHaveLength(PROGRESSIVE_REVEAL.CARD_BATCH)
  })

  it('fills further slots on later frames', () => {
    renderManyItems()

    act(() => {
      vi.advanceTimersToNextFrame()
    })
    act(() => {
      vi.advanceTimersToNextFrame()
    })

    expect(screen.queryAllByText(/^item-/)).toHaveLength(PROGRESSIVE_REVEAL.CARD_BATCH * 2)
  })

  it('fills every slot once the window has grown out', () => {
    renderManyItems()

    for (let frame = 0; frame < 6; frame += 1) {
      act(() => {
        vi.advanceTimersToNextFrame()
      })
    }

    expect(screen.queryAllByText(/^item-/)).toHaveLength(MANY_ITEMS.length)
  })
})
