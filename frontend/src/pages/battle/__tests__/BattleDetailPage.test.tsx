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
import { getBattleSkillIconPath, getHazardPlatePath, getStageBackgroundPath } from '@/shared/assets'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { mountRoute } from '@/test-utils/mountRoute'
import BattlePage from '../BattlePage'
import EnemyDetailPage from '../EnemyDetailPage'
import { battleFilterStore } from '../stores/battleFilterStore'
import { battleModeStore, setBattleMode } from '../stores/battleModeStore'

const LOAD_WAIT_MS = 10000
const TEST_BUDGET_MS = 30000

beforeEach(async () => {
  await i18n.changeLanguage('EN')
  battleFilterStore.resetAll()
  battleModeStore.setState(battleModeStore.getInitialState())
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

function pressed(name: string): string | undefined {
  const group = selectorGroup(name)
  if (group === null) return undefined
  return within(group)
    .getAllByRole('button')
    .find((button) => button.getAttribute('aria-pressed') === 'true')?.textContent
}

function selectIn(group: string, label: string) {
  const found = selectorGroup(group)
  if (found === null) throw new Error(`no ${group} group`)
  fireEvent.click(within(found).getByRole('button', { name: label }))
}

function renderedSkillIds(): string[] {
  return [...requireSlot('detail-sections').querySelectorAll('[data-skill-id]')].map(
    (card) => card.getAttribute('data-skill-id') ?? '',
  )
}

function renderedPassiveIds(): (string | null)[] {
  return [...requireSlot('detail-sections').querySelectorAll('[data-passive-id]')].map((card) =>
    card.getAttribute('data-passive-id'),
  )
}

function metadata() {
  return within(requireSlot('detail-metadata'))
}

function stageEffectKeywords(): (string | null)[] {
  const block = within(requireSlot('detail-selector')).queryByTestId('battle-stage-effects')
  if (block === null) return []
  return [...block.querySelectorAll('[data-keyword-id]')].map((card) =>
    card.getAttribute('data-keyword-id'),
  )
}

describe('engagement selector', () => {
  it(
    'opens 1366 on its first Mirror Dungeon row when arriving from Mirror Dungeon',
    async () => {
      setBattleMode('mirrorDungeon')
      await mountEnemy('1366')

      expect(pressed('MD')).toMatch(/\(Lv\. 60\) \(1\)$/)
      expect(pressed('Story')).toBeUndefined()
    },
    TEST_BUDGET_MS,
  )

  it(
    'opens 1366 on its first story row and numbers the units of stage 10570',
    async () => {
      await mountEnemy('1366')

      const night = 'The 8th Walpurgis Night · Normal (Lv. 45)'
      expect(pressed('Story')).toBe(`${night} (1)`)
      expect(groupButtons('Story')).toEqual(
        expect.arrayContaining([`${night} (1)`, `${night} (2)`, `${night} (3)`]),
      )
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows the Brazen Bull head only on a BrassBull_Second row',
    async () => {
      await mountEnemy('8041')

      expect(pressed('Story')).toBe('4-3 (Lv. 29)')
      expect(selectorGroup('Part')).toBeNull()
      expect(renderedSkillIds()).toEqual(['804101', '804102', '804103', '804104'])

      selectIn('Story', '4-6 (Lv. 30)')

      await waitFor(() => expect(groupButtons('Part')).toEqual(['All', 'Body', 'Head']))
      expect(renderedSkillIds()).toEqual([
        '804101',
        '804102',
        '804103',
        '804104',
        '804105',
        '804106',
      ])
    },
    TEST_BUDGET_MS,
  )

  it(
    'labels an RPG row by node and floor',
    async () => {
      await mountEnemy('1474')

      expect(groupButtons('Story')).toContain('10-5 · Floor B1 (Lv. 64)')
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
})

describe('stage effects', () => {
  it(
    'shows the keywords stage 10710 grants, without the unit own passives',
    async () => {
      await mountEnemy('8390')

      await waitFor(() => expect(stageEffectKeywords()).toContain('UnfinishedDream'))
      const keywords = stageEffectKeywords()
      expect(keywords).not.toContain('FragmentOfHope')
      expect(keywords).not.toContain('StarvingDonqui')
      expect(keywords).not.toContain('UnfinishedDreamTwo')
      expect(new Set(keywords).size).toBe(keywords.length)
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows no block for a row without granted buffs',
    async () => {
      await mountEnemy('8016')

      selectIn('Story', '4-12 (Lv. 31)')

      await waitFor(() => expect(pressed('Story')).toBe('4-12 (Lv. 31)'))
      expect(
        within(requireSlot('detail-selector')).queryByTestId('battle-stage-effects'),
      ).toBeNull()
    },
    TEST_BUDGET_MS,
  )
})

describe('parts', () => {
  it(
    'summarizes group 8063 under All and narrows Head to its status, skills and passive',
    async () => {
      await mountEnemy('8063')

      expect(groupButtons('Part')).toEqual(['All', 'Head', 'Shell'])
      const lines = metadata().getAllByTestId('battle-part-hp')
      expect(lines.map((line) => line.textContent)).toEqual(['Head357', 'Shell102'])
      expect(metadata().getAllByTestId('battle-status-block')).toHaveLength(1)
      expect(metadata().queryByText('552')).not.toBeNull()
      expect(metadata().queryByText('Resistances')).toBeNull()
      expect(metadata().queryByText('Stagger')).toBeNull()
      expect(renderedPassiveIds()).toEqual(expect.arrayContaining(['806301']))

      selectIn('Part', 'Head')

      await waitFor(() =>
        expect(renderedSkillIds()).toEqual(['806301', '806302', '806303', '806305', '806307']),
      )
      const blocks = metadata().getAllByTestId('battle-status-block')
      expect(blocks).toHaveLength(1)
      const [head] = blocks
      if (head === undefined) throw new Error('no status block')
      expect(within(head).getByText('Head')).toBeDefined()
      expect(within(head).getByText('357')).toBeDefined()
      expect(within(head).getByText('Resistances')).toBeDefined()
      expect(within(head).getByText('Stagger')).toBeDefined()
      expect(metadata().queryAllByTestId('battle-part-hp')).toHaveLength(0)
      expect(renderedPassiveIds()).toEqual(['806301'])
    },
    TEST_BUDGET_MS,
  )
})

describe('header', () => {
  it(
    'draws the first engagement map behind the sprite',
    async () => {
      await mountEnemy('1366')

      const background = within(requireSlot('detail-header')).getByTestId(
        'battle-detail-background',
      )
      expect(background.getAttribute('src')).toBe(getStageBackgroundPath('Walpu8_WelfareHallway'))
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows the hazard plate for a ZAYIN enemy',
    async () => {
      await mountEnemy('8016')

      const plate = within(requireSlot('detail-header')).getByTestId('battle-hazard-plate')
      expect(plate.getAttribute('alt')).toBe('ZAYIN')
      expect(plate.getAttribute('src')).toBe(getHazardPlatePath('ZAYIN'))
      expect(within(requireSlot('detail-header')).queryByTestId('battle-hazard-grade')).toBeNull()
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows the hazard grade beside the plate when the enemy has one',
    async () => {
      await mountEnemy('8041')

      const header = within(requireSlot('detail-header'))
      expect(header.getByTestId('battle-hazard-plate').getAttribute('src')).toBe(
        getHazardPlatePath('TETH'),
      )
      expect(header.getByTestId('battle-hazard-grade')).toHaveTextContent('03')
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows no plate for a HYPHEN enemy',
    async () => {
      await mountEnemy('8678')

      expect(within(requireSlot('detail-header')).queryByTestId('battle-hazard-plate')).toBeNull()
    },
    TEST_BUDGET_MS,
  )
})

describe('skills', () => {
  it(
    'shows the flavor of skill 132209 on enemy 1322',
    async () => {
      await mountEnemy('1322')

      const card = requireSlot('detail-sections').querySelector<HTMLElement>(
        '[data-skill-id="132209"]',
      )
      if (card === null) throw new Error('no skill card')
      expect(await within(card).findByTestId('skill-flavor')).toHaveTextContent(
        'A technique that cuts through space itself.',
      )
    },
    TEST_BUDGET_MS,
  )

  it(
    'reads the battle skill icon and keeps only the frame when the file is missing',
    async () => {
      await mountEnemy('1322')

      const card = requireSlot('detail-sections').querySelector<HTMLElement>(
        '[data-skill-id="132209"]',
      )
      if (card === null) throw new Error('no skill card')
      const icon = card.querySelector('img.object-cover')
      if (icon === null) throw new Error('no skill icon')
      expect(icon.getAttribute('src')).toBe(getBattleSkillIconPath('132209'))

      fireEvent.error(icon)

      expect(card.querySelector('img.object-cover')).toBeNull()
      expect(within(card).queryByText('Missing')).toBeNull()
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
      const lockOf = (passiveId: string) =>
        section.querySelector(`[data-passive-id="${passiveId}"]`)?.getAttribute('data-locked')
      expect(lockOf('2040211')).toBe('false')
      expect(lockOf('2040212')).toBeUndefined()
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

      const card = await screen.findByTestId('battle-card-99001', {}, { timeout: LOAD_WAIT_MS })
      fireEvent.click(card)

      expect(await screen.findByTestId('enemy-detail-route')).toBeDefined()
      expect(router.state.location.pathname).toBe('/battle/enemy/99001')
    },
    TEST_BUDGET_MS,
  )
})

describe('restored coverage', () => {
  it(
    'shows no phase selector for single-phase group 8063',
    async () => {
      await mountEnemy('8063')

      expect(selectorGroup('Phase')).toBeNull()
    },
    TEST_BUDGET_MS,
  )

  it(
    'renders no Encounters panel on a detail page',
    async () => {
      await mountEnemy('8044')

      expect(metadata().queryByTestId('battle-engagements')).toBeNull()
      expect(metadata().queryByText('Encounters')).toBeNull()
    },
    TEST_BUDGET_MS,
  )

  it.each([
    ['1082', 'HE'],
    ['1123', 'WAW'],
  ])(
    'shows the hazard plate of %s as %s',
    async (id, classType) => {
      await mountEnemy(id)

      const plate = within(requireSlot('detail-header')).getByTestId('battle-hazard-plate')
      expect(plate.getAttribute('src')).toBe(getHazardPlatePath(classType))
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows no plate for an UNDEF enemy',
    async () => {
      await mountEnemy('1366')

      expect(within(requireSlot('detail-header')).queryByTestId('battle-hazard-plate')).toBeNull()
    },
    TEST_BUDGET_MS,
  )
})

describe('stage effects left to the stage', () => {
  it(
    'shows UnstoppableFunny on railway stage 503',
    async () => {
      await mountEnemy('8678')

      await waitFor(() => expect(stageEffectKeywords()).toContain('UnstoppableFunny'))
    },
    TEST_BUDGET_MS,
  )
})

describe('sanity', () => {
  it(
    'shows the low-morale effect of enemy 8627 beside its panic',
    async () => {
      await mountEnemy('8627')

      const lowMorale = await within(requireSlot('detail-sections')).findByText(
        '·Low Morale Effects',
      )
      const block = lowMorale.closest('[data-slot="low-morale-effect"]')
      expect(block?.textContent).toContain('(3 times per turn)')
      expect(within(requireSlot('detail-sections')).getByText('·Panic Effects')).toBeDefined()
    },
    TEST_BUDGET_MS,
  )
})

describe('client text styles', () => {
  it(
    'draws the OrangeMark clauses of passive 142302 as styled runs around their keyword chips',
    async () => {
      await mountEnemy('1422')

      selectIn('Phase', 'Phase 2')

      const card = await waitFor(() => {
        const found = requireSlot('detail-sections').querySelector<HTMLElement>(
          '[data-passive-id="142302"]',
        )
        if (found === null) throw new Error('no passive 142302')
        return found
      })
      const marks = await waitFor(() => {
        const found = [...card.querySelectorAll<HTMLElement>('[data-text-style="OrangeMark"]')]
        expect(found.length).toBeGreaterThan(1)
        return found
      })
      const [first] = marks
      expect(first?.textContent).toContain('Gain 8')
      expect(first?.querySelector('button')).not.toBeNull()
      expect(card.textContent).not.toContain('<style')
    },
    TEST_BUDGET_MS,
  )
})
