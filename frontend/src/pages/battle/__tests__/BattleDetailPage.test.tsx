import { describe, it, expect, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import {
  RouterProvider,
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
} from '@tanstack/react-router'
import type { ReactNode } from 'react'

import i18n from '@/lib/i18n'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { mountRoute } from '@/test-utils/mountRoute'
import BattlePage from '../BattlePage'
import EnemyDetailPage from '../EnemyDetailPage'
import { enemyFilterStore, setEnemyCases } from '../stores/enemyFilterStore'
import { setBattleType } from '../stores/battleTypeStore'

const LOAD_WAIT_MS = 10000
const TEST_BUDGET_MS = 30000

beforeEach(async () => {
  await i18n.changeLanguage('EN')
  enemyFilterStore.resetAll()
  setBattleType('enemy')
})

function requireSlot(name: string): HTMLElement {
  const element = document.querySelector<HTMLElement>(`[data-slot="${name}"]`)
  if (element === null) throw new Error(`no ${name} slot rendered`)
  return element
}

async function mountEnemy(id: string) {
  mountRoute('/battle/enemy/$id', `/battle/enemy/${id}`, EnemyDetailPage)
  await waitFor(() => expect(requireSlot('detail-sections')).toBeDefined(), {
    timeout: LOAD_WAIT_MS,
  })
  await waitFor(() => expect(document.querySelectorAll('[data-slot="skeleton"]').length).toBe(0), {
    timeout: LOAD_WAIT_MS,
  })
}

function selectorGroup(name: string) {
  return within(requireSlot('detail-selector')).queryByRole('group', { name })
}

function groupButtons(name: string): string[] {
  const group = selectorGroup(name)
  if (group === null) return []
  return within(group)
    .getAllByRole('button')
    .map((button) => button.textContent ?? '')
}

function renderedSkillIds(): string[] {
  return [...requireSlot('detail-sections').querySelectorAll('[data-skill-id]')].map(
    (card) => card.getAttribute('data-skill-id') ?? '',
  )
}

function statusBlocks(): HTMLElement[] {
  return within(requireSlot('detail-metadata')).queryAllByTestId('battle-status-block')
}

function pressedLevel(): string | undefined {
  const group = selectorGroup('Level')
  if (group === null) return undefined
  return within(group)
    .getAllByRole('button')
    .find((button) => button.getAttribute('aria-pressed') === 'true')?.textContent
}

describe('enemy detail page', () => {
  it(
    'offers All/Head/Body for 1280 and narrows Head to its skills, status and passive',
    async () => {
      await mountEnemy('1280')

      expect(groupButtons('Part')).toEqual(['All', 'Head', 'Body'])

      fireEvent.click(
        within(selectorGroup('Part') as HTMLElement).getByRole('button', { name: 'Head' }),
      )

      await waitFor(() =>
        expect(renderedSkillIds()).toEqual(['806301', '806302', '806303', '806305', '806307']),
      )
      const blocks = statusBlocks()
      expect(blocks).toHaveLength(1)
      const [head] = blocks
      if (head === undefined) throw new Error('no status block')
      expect(within(head).getByText('Head')).toBeDefined()
      expect(within(head).getByText('434')).toBeDefined()
      expect(within(requireSlot('detail-metadata')).queryByText('124')).toBeNull()
      expect(
        await within(requireSlot('detail-sections')).findByText('Ferocious Acidic Crust'),
      ).toBeDefined()
      expect(
        [...requireSlot('detail-sections').querySelectorAll('[data-passive-id]')].map((card) =>
          card.getAttribute('data-passive-id'),
        ),
      ).toEqual(['806301'])
    },
    TEST_BUDGET_MS,
  )

  it(
    'draws skills without an icon as the frame only',
    async () => {
      await mountEnemy('1280')

      const card = requireSlot('detail-sections').querySelector<HTMLElement>(
        '[data-skill-id="806301"]',
      )
      if (card === null) throw new Error('no skill card')
      expect(card.querySelector('img.object-cover')).toBeNull()
      expect(within(card).queryByText('Missing')).toBeNull()
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows a phase selector with four phases for 8712',
    async () => {
      await mountEnemy('8712')

      expect(groupButtons('Phase')).toHaveLength(4)
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows no phase selector for single-phase 1280',
    async () => {
      await mountEnemy('1280')

      expect(selectorGroup('Phase')).toBeNull()
    },
    TEST_BUDGET_MS,
  )

  it(
    'offers the distinct engagement levels and recomputes HP from the selected level',
    async () => {
      await mountEnemy('1080')

      expect(groupButtons('Level')).toEqual(['Lv. 50', 'Lv. 51', 'Lv. 60'])
      expect(pressedLevel()).toBe('Lv. 51')
      const [unit] = statusBlocks()
      if (unit === undefined) throw new Error('no status block')
      expect(within(unit).getByText('469')).toBeDefined()

      fireEvent.click(
        within(selectorGroup('Level') as HTMLElement).getByRole('button', { name: 'Lv. 60' }),
      )

      await waitFor(() => expect(within(unit).getByText('534')).toBeDefined())
    },
    TEST_BUDGET_MS,
  )

  it(
    'defaults the level to the first engagement of the case the list was filtered to',
    async () => {
      setEnemyCases(new Set(['mirrorDungeon']))
      await mountEnemy('1080')

      expect(pressedLevel()).toBe('Lv. 50')
    },
    TEST_BUDGET_MS,
  )

  it(
    'groups engagement labels by case in the metadata panel',
    async () => {
      await mountEnemy('8044')

      const panel = await within(requireSlot('detail-metadata')).findByTestId('battle-engagements')
      const story = panel.querySelector<HTMLElement>('[data-case="story"]')
      if (story === null) throw new Error('no story group')
      expect(within(story).getByText('Story')).toBeDefined()
      expect(within(story).getByText('4-48 (Lv. 35)')).toBeDefined()
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows the E.G.O skill and passive cards of a Peccatulum Invidiae at its E.G.O level',
    async () => {
      await mountEnemy('1110')

      const section = within(requireSlot('detail-sections'))
        .getAllByRole('region')
        .find((region) => within(region).queryByRole('heading', { level: 2, name: 'E.G.O' }))
      if (section === undefined) throw new Error('no E.G.O section')
      await waitFor(() =>
        expect(
          [...section.querySelectorAll('[data-ego-id]')].map((block) =>
            block.getAttribute('data-ego-id'),
          ),
        ).toEqual(['20401', '20403', '20402', '20407']),
      )
      expect(
        [...section.querySelectorAll('[data-skill-id]')].map((card) =>
          card.getAttribute('data-skill-id'),
        ),
      ).toEqual(['2040111', '2040311', '2040321', '2040211', '2040221', '2040711', '2040721'])
      expect(await within(section).findByText('Art')).toBeDefined()
      const card = (skillId: string): HTMLElement => {
        const found = section.querySelector<HTMLElement>(`[data-skill-id="${skillId}"]`)
        if (found === null) throw new Error(`no skill card ${skillId}`)
        return found
      }
      expect(within(card('2040111')).getByText('+10')).toBeDefined()
      expect(within(card('2040221')).getByText('-22')).toBeDefined()
      const lockOf = (passiveId: string) =>
        section.querySelector(`[data-passive-id="${passiveId}"]`)?.getAttribute('data-locked')
      expect(lockOf('2040211')).toBe('false')
      expect(lockOf('2040212')).toBeUndefined()
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows the id when the entity has no name',
    async () => {
      await mountEnemy('71035')

      expect(
        within(requireSlot('detail-header')).getByRole('heading', { level: 1 }).textContent,
      ).toBe('71035')
    },
    TEST_BUDGET_MS,
  )
})

describe('battle list cards', () => {
  it(
    'navigate to the detail route of the clicked card',
    async () => {
      const rootRoute = createRootRoute()
      const listRoute = createRoute({
        getParentRoute: () => rootRoute,
        path: '/battle',
        component: BattlePage,
      })
      const detailRoute = createRoute({
        getParentRoute: () => rootRoute,
        path: '/battle/enemy/$id',
        component: (): ReactNode => <div data-testid="enemy-detail-route" />,
      })
      const router = createRouter({
        routeTree: rootRoute.addChildren([listRoute, detailRoute]),
        history: createMemoryHistory({ initialEntries: ['/battle'] }),
        defaultPendingMs: 0,
      })
      render(
        <QueryClientProvider client={createTestQueryClient()}>
          <RouterProvider router={router} />
        </QueryClientProvider>,
      )

      const card = await screen.findByTestId('battle-card-1144', {}, { timeout: LOAD_WAIT_MS })
      fireEvent.click(card)

      expect(await screen.findByTestId('enemy-detail-route')).toBeDefined()
      expect(router.state.location.pathname).toBe('/battle/enemy/1144')
    },
    TEST_BUDGET_MS,
  )
})
