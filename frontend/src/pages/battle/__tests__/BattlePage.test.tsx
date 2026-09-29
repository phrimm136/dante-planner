import { describe, it, expect, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'

import i18n from '@/lib/i18n'
import { mountRoute } from '@/test-utils/mountRoute'
import BattlePage from '../BattlePage'
import { assistantFilterStore } from '../stores/assistantFilterStore'
import { battleTypeStore } from '../stores/battleTypeStore'
import { enemyFilterStore } from '../stores/enemyFilterStore'
import { sinnerEventFilterStore } from '../stores/sinnerEventFilterStore'

const LOAD_WAIT_MS = 10000

beforeEach(async () => {
  await i18n.changeLanguage('EN')
  enemyFilterStore.resetAll()
  assistantFilterStore.resetAll()
  sinnerEventFilterStore.resetAll()
  battleTypeStore.setState(battleTypeStore.getInitialState())
})

function renderPage() {
  mountRoute('/battle', '/battle', BattlePage)
}

function modeRow() {
  return within(screen.getByRole('group', { name: 'Mode' }))
}

function desktop() {
  const aside = document.querySelector('aside')
  if (aside === null) throw new Error('no desktop filter sidebar')
  return within(aside)
}

function mobileContainer(): HTMLElement {
  const aside = document.querySelector('aside')
  const mobile = aside?.nextElementSibling
  if (!(mobile instanceof HTMLElement)) throw new Error('no mobile filter panel')
  return mobile
}

function sectionTitles(container: HTMLElement): string[] {
  return Array.from(container.querySelectorAll('span.uppercase')).map(
    (span) => span.firstChild?.textContent ?? '',
  )
}

function slotHidden(id: string): boolean | undefined {
  const card = screen.queryByTestId(`battle-card-${id}`)
  if (card === null) return undefined
  return card.parentElement?.classList.contains('hidden')
}

function renderedCardIds(): string[] {
  return [...document.querySelectorAll('[data-testid^="battle-card-"]')]
    .map((card) => card.getAttribute('data-testid') ?? '')
    .filter((testId) => /^battle-card-\d+$/.test(testId))
    .map((testId) => testId.replace('battle-card-', ''))
}

async function waitForList() {
  await waitFor(() => expect(desktop().getByPlaceholderText('Search by name...')).toBeVisible(), {
    timeout: LOAD_WAIT_MS,
  })
}

function search(query: string) {
  fireEvent.change(desktop().getByPlaceholderText('Search by name...'), {
    target: { value: query },
  })
}

describe('BattlePage', () => {
  it('shows the mode buttons first, then the type buttons, Story and Enemies selected', async () => {
    renderPage()
    await waitForList()

    const story = modeRow().getByRole('button', { name: 'Story' })
    const enemies = screen.getByRole('button', { name: 'Enemies' })
    expect(
      modeRow()
        .getAllByRole('button')
        .map((button) => button.textContent),
    ).toEqual(['Story', 'Lux', 'MD', 'RR', 'Reflectrial'])
    expect(story.compareDocumentPosition(enemies) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(story).toHaveAttribute('aria-pressed', 'true')
    expect(enemies).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Assist Units' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
    expect(screen.getByRole('button', { name: 'Sinners?' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
  })

  it('selects one mode at a time', async () => {
    renderPage()
    await waitForList()

    fireEvent.click(modeRow().getByRole('button', { name: 'MD' }))

    expect(modeRow().getByRole('button', { name: 'MD' })).toHaveAttribute('aria-pressed', 'true')
    expect(modeRow().getByRole('button', { name: 'Story' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
  })

  it('shows the empty-result state for a type the mode does not engage', async () => {
    renderPage()
    await waitForList()

    fireEvent.click(modeRow().getByRole('button', { name: 'Lux' }))
    fireEvent.click(screen.getByRole('button', { name: 'Assist Units' }))

    expect(
      await screen.findByText(
        'No battle entities match your current filters and search criteria',
        {},
        { timeout: LOAD_WAIT_MS },
      ),
    ).toBeInTheDocument()
    expect(renderedCardIds()).toEqual([])
  })

  it('lists the mode entities in the mode order, first cards first', async () => {
    renderPage()
    await waitForList()

    fireEvent.click(modeRow().getByRole('button', { name: 'MD' }))

    await waitFor(() => expect(renderedCardIds().length).toBeGreaterThan(1), {
      timeout: LOAD_WAIT_MS,
    })
    const [first] = renderedCardIds()
    expect(first).toBe('70001')
  })

  it('finds a card by the name its mode shows', async () => {
    renderPage()
    await waitForList()

    search('Zilu')

    await waitFor(
      () => {
        expect(slotHidden('1133')).toBe(false)
      },
      { timeout: LOAD_WAIT_MS },
    )
  })

  it('resets the filters and search only, keeping the mode and type', async () => {
    renderPage()
    await waitForList()

    fireEvent.click(modeRow().getByRole('button', { name: 'RR' }))
    fireEvent.click(
      await desktop().findByRole('button', { name: 'Line 1' }, { timeout: LOAD_WAIT_MS }),
    )
    search('Zilu')
    await waitFor(() => expect(enemyFilterStore.store.getState().searchQuery).toBe('Zilu'))

    fireEvent.click(desktop().getByRole('button', { name: /Reset All/ }))

    const enemyState = enemyFilterStore.store.getState()
    expect(enemyState.searchQuery).toBe('')
    expect(Object.values(enemyState.values).every((selection) => selection.size === 0)).toBe(true)
    expect(battleTypeStore.getState()).toEqual({ mode: 'refractionRailway', entityType: 'enemy' })
    expect(modeRow().getByRole('button', { name: 'RR' })).toHaveAttribute('aria-pressed', 'true')
  })

  it.each([
    ['Story', ['Chapter']],
    ['Lux', ['Luxcavation']],
    ['MD', ['Theme Pack', 'Hidden Node', 'Event']],
    ['RR', ['Line']],
    ['Reflectrial', []],
  ])('shows the drill-down of %s alone', async (modeLabel, titles) => {
    renderPage()
    await waitForList()

    fireEvent.click(modeRow().getByRole('button', { name: modeLabel }))

    await waitFor(() => expect(sectionTitles(mobileContainer())).toEqual(titles))
  })

  it('labels the rerun of line 1 as its BokGak', async () => {
    renderPage()
    await waitForList()

    fireEvent.click(modeRow().getByRole('button', { name: 'RR' }))

    expect(
      await desktop().findByRole('button', { name: 'Line 1 BokGak' }, { timeout: LOAD_WAIT_MS }),
    ).toBeInTheDocument()
    expect(desktop().getByRole('button', { name: 'Line 1' })).toBeInTheDocument()
  })
})

describe('BattlePage restored coverage', () => {
  it('keeps the search across a type switch and a mode switch', async () => {
    renderPage()
    await waitForList()

    search('Zilu')
    fireEvent.click(screen.getByRole('button', { name: 'Assist Units' }))
    fireEvent.click(screen.getByRole('button', { name: 'Enemies' }))
    await waitForList()
    fireEvent.click(modeRow().getByRole('button', { name: 'MD' }))
    await waitForList()

    expect(desktop().getByPlaceholderText('Search by name...')).toHaveValue('Zilu')
    await waitFor(() => expect(slotHidden('1133')).toBe(false), { timeout: LOAD_WAIT_MS })
  })

  it('resets the enemy scope only, keeping the mode, type and the assistant search', async () => {
    renderPage()
    await waitForList()

    fireEvent.click(modeRow().getByRole('button', { name: 'MD' }))
    fireEvent.click(screen.getByRole('button', { name: 'Assist Units' }))
    await waitForList()
    search('Kira')
    await waitFor(() => expect(assistantFilterStore.store.getState().searchQuery).toBe('Kira'))

    fireEvent.click(screen.getByRole('button', { name: 'Enemies' }))
    await waitForList()
    fireEvent.click(await desktop().findByRole('button', { name: 'Hidden Node' }))
    search('Zilu')
    await waitFor(() => expect(enemyFilterStore.store.getState().searchQuery).toBe('Zilu'))

    fireEvent.click(desktop().getByRole('button', { name: /Reset All/ }))

    const enemyState = enemyFilterStore.store.getState()
    expect(enemyState.searchQuery).toBe('')
    expect(Object.values(enemyState.values).every((selection) => selection.size === 0)).toBe(true)
    expect(battleTypeStore.getState()).toEqual({ mode: 'mirrorDungeon', entityType: 'enemy' })
    expect(assistantFilterStore.store.getState().searchQuery).toBe('Kira')
  })

  it('shows the filters on mobile without an expand button', async () => {
    renderPage()
    await waitForList()

    expect(
      within(mobileContainer()).queryByRole('button', { name: 'Expand filters' }),
    ).not.toBeInTheDocument()
  })
})
