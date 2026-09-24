import { describe, it, expect, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'

import i18n from '@/lib/i18n'
import { mountRoute } from '@/test-utils/mountRoute'
import BattlePage from '../BattlePage'
import { assistantFilterStore } from '../stores/assistantFilterStore'
import { battleTypeStore, setBattleType } from '../stores/battleTypeStore'
import { enemyFilterStore } from '../stores/enemyFilterStore'
import { sinnerEventFilterStore } from '../stores/sinnerEventFilterStore'

const LOAD_WAIT_MS = 10000

beforeEach(async () => {
  await i18n.changeLanguage('EN')
  enemyFilterStore.resetAll()
  assistantFilterStore.resetAll()
  sinnerEventFilterStore.resetAll()
  setBattleType('enemy')
})

function renderPage() {
  mountRoute('/battle', '/battle', BattlePage)
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

async function waitForEnemies() {
  await waitFor(() => expect(desktop().getByRole('button', { name: 'Story' })).toBeVisible(), {
    timeout: LOAD_WAIT_MS,
  })
}

function search(query: string) {
  fireEvent.change(desktop().getByPlaceholderText('Search by name...'), {
    target: { value: query },
  })
}

describe('BattlePage', () => {
  it('shows the three type buttons with Enemies selected by default', async () => {
    renderPage()
    await waitForEnemies()

    const enemies = screen.getByRole('button', { name: 'Enemies' })
    const assistants = screen.getByRole('button', { name: 'Assist Units' })
    const sinners = screen.getByRole('button', { name: 'Sinners?' })

    expect(enemies).toHaveAttribute('aria-pressed', 'true')
    expect(enemies).toHaveAttribute('data-selected', 'true')
    expect(enemies).toHaveClass('selectable')
    expect(assistants).toHaveAttribute('aria-pressed', 'false')
    expect(sinners).toHaveAttribute('aria-pressed', 'false')
  })

  it('shows 1144 for Story plus "Zilu" and keeps both across a type switch', async () => {
    renderPage()
    await waitForEnemies()

    fireEvent.click(desktop().getByRole('button', { name: 'Story' }))
    search('Zilu')

    await waitFor(
      () => {
        expect(slotHidden('1144')).toBe(false)
        expect(slotHidden('1016')).toBe(true)
      },
      { timeout: LOAD_WAIT_MS },
    )

    fireEvent.click(screen.getByRole('button', { name: 'Assist Units' }))
    await waitFor(() => expect(screen.getByTestId('battle-card-400001')).toBeInTheDocument(), {
      timeout: LOAD_WAIT_MS,
    })
    expect(desktop().queryByRole('button', { name: 'Story' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Enemies' }))
    await waitForEnemies()

    expect(desktop().getByRole('button', { name: 'Story' })).toHaveAttribute('aria-pressed', 'true')
    expect(desktop().getByPlaceholderText('Search by name...')).toHaveValue('Zilu')
    await waitFor(
      () => {
        expect(slotHidden('1144')).toBe(false)
        expect(slotHidden('1016')).toBe(true)
      },
      { timeout: LOAD_WAIT_MS },
    )
  })

  it('resets only the enemy filters, keeping the type and the assistant search', async () => {
    renderPage()
    await waitForEnemies()

    fireEvent.click(screen.getByRole('button', { name: 'Assist Units' }))
    await waitFor(() => expect(screen.getByTestId('battle-card-400001')).toBeInTheDocument(), {
      timeout: LOAD_WAIT_MS,
    })
    search('Kira')
    await waitFor(() => expect(assistantFilterStore.store.getState().searchQuery).toBe('Kira'))

    fireEvent.click(screen.getByRole('button', { name: 'Enemies' }))
    await waitForEnemies()
    fireEvent.click(desktop().getByRole('button', { name: 'Story' }))
    search('Zilu')
    await waitFor(() => expect(enemyFilterStore.store.getState().searchQuery).toBe('Zilu'))

    fireEvent.click(desktop().getByRole('button', { name: /Reset All/ }))

    const enemyState = enemyFilterStore.store.getState()
    expect(enemyState.searchQuery).toBe('')
    expect(Object.values(enemyState.values).every((selection) => selection.size === 0)).toBe(true)
    expect(battleTypeStore.getState().entityType).toBe('enemy')
    expect(assistantFilterStore.store.getState().searchQuery).toBe('Kira')
    expect(desktop().getByRole('button', { name: 'Story' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
  })

  it('shows the stage filter and its drill-down on mobile without expanding', async () => {
    renderPage()
    await waitForEnemies()

    const mobile = within(mobileContainer())
    expect(mobile.queryByRole('button', { name: 'Expand filters' })).not.toBeInTheDocument()

    fireEvent.click(mobile.getByRole('button', { name: 'Story' }))

    const chapterDropdown = await mobile.findByRole('combobox', {}, { timeout: LOAD_WAIT_MS })
    expect(chapterDropdown).toBeVisible()
    expect(chapterDropdown).toHaveTextContent('Chapter')
    expect(sectionTitles(mobileContainer())).toEqual(['Mode', 'Chapter'])
  })

  it.each([
    ['Story', ['Mode', 'Chapter']],
    ['Lux', ['Mode', 'Luxcavation']],
    ['MD', ['Mode', 'Theme Pack', 'Hidden Node', 'Event']],
    ['RR', ['Mode', 'Line']],
    ['Reflectrial', ['Mode']],
  ])('shows the drill-down of %s alone', async (caseLabel, titles) => {
    renderPage()
    await waitForEnemies()

    expect(sectionTitles(mobileContainer())).toEqual(['Mode'])
    fireEvent.click(desktop().getByRole('button', { name: caseLabel }))

    await waitFor(() => expect(sectionTitles(mobileContainer())).toEqual(titles))
  })

  it('labels the rerun of line 1 as its BokGak', async () => {
    renderPage()
    await waitForEnemies()

    fireEvent.click(desktop().getByRole('button', { name: 'RR' }))

    expect(
      await desktop().findByRole('button', { name: 'Line 1 BokGak' }, { timeout: LOAD_WAIT_MS }),
    ).toBeInTheDocument()
    expect(desktop().getByRole('button', { name: 'Line 1' })).toBeInTheDocument()
  })
})
