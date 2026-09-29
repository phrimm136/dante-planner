import { describe, it, expect, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'

import i18n from '@/lib/i18n'
import { mountRoute } from '@/test-utils/mountRoute'
import BattlePage from '../BattlePage'
import { battleFilterStore } from '../stores/battleFilterStore'
import { battleModeStore } from '../stores/battleModeStore'

const LOAD_WAIT_MS = 10000

beforeEach(async () => {
  await i18n.changeLanguage('EN')
  battleFilterStore.resetAll()
  battleModeStore.setState(battleModeStore.getInitialState())
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

function typeButton(name: string) {
  return desktop().getByRole('button', { name })
}

describe('BattlePage', () => {
  it('shows the mode buttons first and the type filter in the panel, all types by default', async () => {
    renderPage()
    await waitForList()

    const story = modeRow().getByRole('button', { name: 'Story' })
    expect(
      modeRow()
        .getAllByRole('button')
        .map((button) => button.textContent),
    ).toEqual(['Story', 'Lux', 'MD', 'RR', 'Reflectrial'])
    expect(story).toHaveAttribute('aria-pressed', 'true')
    for (const name of ['Enemies', 'Assist Units', 'Sinners?']) {
      expect(typeButton(name)).toHaveAttribute('aria-pressed', 'false')
      expect(
        story.compareDocumentPosition(typeButton(name)) & Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy()
    }
    expect(sectionTitles(mobileContainer())[0]).toBe('Type')
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

  it('selects several types at once and narrows the grid to them', async () => {
    renderPage()
    await waitForList()

    fireEvent.click(typeButton('Assist Units'))
    fireEvent.click(typeButton('Sinners?'))

    expect(typeButton('Assist Units')).toHaveAttribute('aria-pressed', 'true')
    expect(typeButton('Sinners?')).toHaveAttribute('aria-pressed', 'true')
    await waitFor(() => expect(slotHidden('400001')).toBe(false), { timeout: LOAD_WAIT_MS })
    await waitFor(() => expect(slotHidden('99001')).toBe(true), { timeout: LOAD_WAIT_MS })
  })

  it('shows the empty-result state for a type the mode does not engage', async () => {
    renderPage()
    await waitForList()

    fireEvent.click(modeRow().getByRole('button', { name: 'Lux' }))
    fireEvent.click(typeButton('Assist Units'))

    expect(
      await screen.findByText(
        'No battle entities match your current filters and search criteria',
        {},
        { timeout: LOAD_WAIT_MS },
      ),
    ).toBeInTheDocument()
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

  it('resets the type filter with the other filters and the search, keeping the mode', async () => {
    renderPage()
    await waitForList()

    fireEvent.click(modeRow().getByRole('button', { name: 'RR' }))
    fireEvent.click(typeButton('Enemies'))
    fireEvent.click(
      await desktop().findByRole('button', { name: 'Line 1' }, { timeout: LOAD_WAIT_MS }),
    )
    search('Zilu')
    await waitFor(() => expect(battleFilterStore.store.getState().searchQuery).toBe('Zilu'))

    fireEvent.click(desktop().getByRole('button', { name: /Reset All/ }))

    const state = battleFilterStore.store.getState()
    expect(state.searchQuery).toBe('')
    expect(Object.values(state.values).every((selection) => selection.size === 0)).toBe(true)
    expect(battleModeStore.getState().mode).toBe('refractionRailway')
    expect(modeRow().getByRole('button', { name: 'RR' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('keeps the search across type-filter changes and a mode switch', async () => {
    renderPage()
    await waitForList()

    search('Zilu')
    fireEvent.click(typeButton('Assist Units'))
    fireEvent.click(typeButton('Assist Units'))
    fireEvent.click(modeRow().getByRole('button', { name: 'MD' }))
    await waitForList()

    expect(desktop().getByPlaceholderText('Search by name...')).toHaveValue('Zilu')
    await waitFor(() => expect(slotHidden('1133')).toBe(false), { timeout: LOAD_WAIT_MS })
  })

  it.each([
    ['Story', ['Type', 'Chapter']],
    ['Lux', ['Type', 'Luxcavation']],
    ['MD', ['Type', 'Theme Pack', 'Hidden Node', 'Event']],
    ['RR', ['Type', 'Line']],
    ['Reflectrial', ['Type']],
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

  it('shows the filters on mobile without an expand button', async () => {
    renderPage()
    await waitForList()

    expect(
      within(mobileContainer()).queryByRole('button', { name: 'Expand filters' }),
    ).not.toBeInTheDocument()
  })
})
