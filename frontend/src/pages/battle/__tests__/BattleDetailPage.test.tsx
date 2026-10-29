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
import {
  getBattleKeywordIconPath,
  getBattleSkillIconPath,
  getBattleSpritePath,
  getHazardGradedPlatePath,
  getHazardHiddenPlatePath,
  getHazardPlatePath,
  getHazardSupplementPlatePath,
  getLevelNumberHyphenPlatePath,
  getLevelNumberUndefPlatePath,
  getPanicIconPath,
  getStageBackgroundPath,
} from '@/shared/assets'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { mountRoute } from '@/test-utils/mountRoute'
import AssistantDetailPage from '../AssistantDetailPage'
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

function stageQuery(stage: string | undefined): string {
  return stage === undefined ? '' : `?stage=${encodeURIComponent(stage)}`
}

async function settled() {
  await waitFor(() => expect(requireSlot('detail-sections')).toBeDefined(), {
    timeout: LOAD_WAIT_MS,
  })
  await waitFor(() => expect(document.querySelectorAll('[data-slot="skeleton"]').length).toBe(0), {
    timeout: LOAD_WAIT_MS,
  })
}

async function mountEnemy(id: string, stage?: string) {
  mountRoute('/battle/enemy/$id', `/battle/enemy/${id}${stageQuery(stage)}`, EnemyDetailPage)
  await settled()
}

async function mountAssistant(id: string, stage?: string) {
  mountRoute(
    '/battle/assistant/$id',
    `/battle/assistant/${id}${stageQuery(stage)}`,
    AssistantDetailPage,
  )
  await settled()
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

function statCell(alt: string): string | null | undefined {
  return requireSlot('detail-metadata').querySelector(`img[alt="${alt}"]`)?.parentElement
    ?.textContent
}

function statCells(alts: readonly string[]): (string | null | undefined)[] {
  return alts.map(statCell)
}

const SIN_CELLS = ['CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK', 'AZURE', 'INDIGO', 'VIOLET']
const RESIST_CELLS = ['Slash', 'Pierce', 'Blunt', ...SIN_CELLS]
const WHOLE_BLANK_CELLS = ['Speed', 'Defense', ...RESIST_CELLS]

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
      expect(groupButtons('Part')).toEqual(['All', 'Body'])
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
    'labels an RPG row by route and floor',
    async () => {
      await mountEnemy('1474')

      expect(groupButtons('Story')).toContain('10-B B1F (Lv. 64)')
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows a phase selector with four phases for 8712 on card 8002',
    async () => {
      setBattleMode('luxcavation')
      await mountEnemy('8002')

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

      selectIn('Story', '4-54 (Lv. 31)')

      await waitFor(() => expect(pressed('Story')).toBe('4-54 (Lv. 31)'))
      expect(
        within(requireSlot('detail-selector')).queryByTestId('battle-stage-effects'),
      ).toBeNull()
    },
    TEST_BUDGET_MS,
  )
})

describe('parts', () => {
  it(
    'shows only the whole HP of group 8063 under All and narrows Head to its status, skills and passive',
    async () => {
      await mountEnemy('8063')

      expect(groupButtons('Part')).toEqual(['All', 'Head', 'Shell'])
      expect(metadata().queryAllByTestId('battle-part-hp')).toHaveLength(0)
      expect(metadata().getAllByTestId('battle-status-block')).toHaveLength(1)
      expect(statCell('HP')).toBe('552')
      expect(statCells(WHOLE_BLANK_CELLS)).toEqual(WHOLE_BLANK_CELLS.map(() => '-'))
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
      expect(within(head).queryByText('Head')).toBeNull()
      expect(within(head).getByText('357')).toBeDefined()
      expect(within(head).getByText('Resistances')).toBeDefined()
      expect(within(head).getByText('Stagger')).toBeDefined()
      expect(metadata().queryAllByTestId('battle-part-hp')).toHaveLength(0)
      expect(renderedPassiveIds()).toEqual(['806301'])
    },
    TEST_BUDGET_MS,
  )
})

describe('single-part units', () => {
  it(
    'offers All and the one part of 1347 and draws its skills at the row level',
    async () => {
      await mountEnemy('1347')

      const parts = groupButtons('Part')
      expect(parts).toHaveLength(2)
      expect(parts[0]).toBe('All')

      const card = requireSlot('detail-sections').querySelector('[data-skill-id="134707"]')
      expect(card?.querySelector('[data-slot="skill-level"]')?.textContent).toBe('88')
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows no speed or multiplier for the whole of one-part 1347 and shows them for its part',
    async () => {
      await mountEnemy('1347')

      expect(statCells(WHOLE_BLANK_CELLS)).toEqual(WHOLE_BLANK_CELLS.map(() => '-'))
      expect(metadata().queryByText(/\(x/)).toBeNull()

      const [, part] = groupButtons('Part')
      if (part === undefined) throw new Error('no part button')
      selectIn('Part', part)

      await waitFor(() => expect(statCell('Speed')).toMatch(/^\d+-\d+$/))
      expect(statCell('Slash')).toBe('Weak(x1.2)')
    },
    TEST_BUDGET_MS,
  )
})

describe('abnormality whole', () => {
  it(
    'draws "-" for every resistance of two-part 1003 under All, with no part lines or stagger',
    async () => {
      await mountEnemy('1003')

      expect(groupButtons('Part')).toEqual(['All', 'Shell', 'Pearl'])
      expect(statCells(RESIST_CELLS)).toEqual(RESIST_CELLS.map(() => '-'))
      expect(metadata().queryAllByTestId('battle-part-hp')).toHaveLength(0)
      expect(metadata().queryByText('Stagger')).toBeNull()
      expect(metadata().queryByText(/\(x/)).toBeNull()
    },
    TEST_BUDGET_MS,
  )

  it(
    'reads CRIMSON and INDIGO of 1003 part Pearl as Weak (x1.2)',
    async () => {
      await mountEnemy('1003')

      selectIn('Part', 'Pearl')

      await waitFor(() => expect(statCell('CRIMSON')).toBe('Weak(x1.2)'))
      expect(statCell('INDIGO')).toBe('Weak(x1.2)')
    },
    TEST_BUDGET_MS,
  )

  it(
    'keeps the speed, defense and resistances of shadow 8678 under All',
    async () => {
      setBattleMode('refractionRailway')
      await mountEnemy('1217')

      expect(pressed('Part')).toBe('All')
      expect(statCell('Speed')).toMatch(/^\d+-\d+$/)
      expect(statCell('Defense')).toMatch(/^\d+ \(\+3\)$/)
      expect(statCell('Slash')).toBe('Fatal(x2)')
      expect(statCell('INDIGO')).toBe('Endure(x0.75)')
      expect(metadata().queryByText('Stagger')).not.toBeNull()
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
    'draws the selected engagement map behind the sprite',
    async () => {
      await mountEnemy('8041')

      const header = within(requireSlot('detail-header'))
      expect(header.getByTestId('battle-detail-background').getAttribute('src')).toBe(
        getStageBackgroundPath('Cp4_BrokenKCorpStreet_v1'),
      )
      const stories = groupButtons('Story')
      selectIn('Story', stories[stories.length - 1] ?? '')

      await waitFor(
        () =>
          expect(
            within(requireSlot('detail-header'))
              .getByTestId('battle-detail-background')
              .getAttribute('src'),
          ).toBe(getStageBackgroundPath('Cp4_TcorStreet')),
        { timeout: LOAD_WAIT_MS },
      )
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
    'draws a graded TETH enemy on the graded plate with its grade in the plate',
    async () => {
      await mountEnemy('8041')

      const header = within(requireSlot('detail-header'))
      expect(header.getByTestId('battle-hazard-plate').getAttribute('src')).toBe(
        getHazardGradedPlatePath('TETH'),
      )
      const grade = header.getByTestId('battle-hazard-grade')
      expect(grade.textContent).toBe('-03')
      expect(grade.style.color).toBe('#f8c200')
    },
    TEST_BUDGET_MS,
  )

  it(
    'draws the hyphen level-number plate for a HYPHEN enemy',
    async () => {
      setBattleMode('refractionRailway')
      await mountEnemy('1217')

      const header = within(requireSlot('detail-header'))
      expect(header.getByTestId('battle-hazard-plate').getAttribute('src')).toBe(
        getLevelNumberHyphenPlatePath(),
      )
      expect(header.queryByTestId('battle-hazard-grade')).toBeNull()
    },
    TEST_BUDGET_MS,
  )

  it(
    'draws the blank plate and the grade for a SUPPLEMENT enemy',
    async () => {
      await mountEnemy('1004')

      const header = within(requireSlot('detail-header'))
      expect(header.getByTestId('battle-hazard-plate').getAttribute('src')).toBe(
        getHazardSupplementPlatePath(),
      )
      expect(header.getByTestId('battle-hazard-grade').textContent).toBe('-01')
    },
    TEST_BUDGET_MS,
  )

  it(
    'draws the hidden plate without a grade for a HIDDEN enemy',
    async () => {
      await mountEnemy('8043')

      const header = within(requireSlot('detail-header'))
      expect(header.getByTestId('battle-hazard-plate').getAttribute('src')).toBe(
        getHazardHiddenPlatePath(),
      )
      expect(header.queryByTestId('battle-hazard-grade')).toBeNull()
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
    ['1082', 'HE', '#f8c200'],
    ['1123', 'WAW', '#ff9600'],
  ])(
    'shows the graded hazard plate of %s as %s with its grade in %s',
    async (id, classType, color) => {
      await mountEnemy(id)

      const header = within(requireSlot('detail-header'))
      expect(header.getByTestId('battle-hazard-plate').getAttribute('src')).toBe(
        getHazardGradedPlatePath(classType),
      )
      expect(header.getByTestId('battle-hazard-grade').style.color).toBe(color)
    },
    TEST_BUDGET_MS,
  )

  it(
    'draws the page language UNDEF plate for an UNDEF enemy',
    async () => {
      await mountEnemy('1366')

      const plate = within(requireSlot('detail-header')).getByTestId('battle-hazard-plate')
      expect(plate.getAttribute('src')).toBe(getLevelNumberUndefPlatePath('en'))

      await i18n.changeLanguage('KR')

      await waitFor(
        () =>
          expect(
            within(requireSlot('detail-header'))
              .getByTestId('battle-hazard-plate')
              .getAttribute('src'),
          ).toBe(getLevelNumberUndefPlatePath('kr')),
        { timeout: LOAD_WAIT_MS },
      )
    },
    TEST_BUDGET_MS,
  )
})

describe('stage effects left to the stage', () => {
  it(
    'shows UnstoppableFunny on railway stage 503',
    async () => {
      setBattleMode('refractionRailway')
      await mountEnemy('1217')

      await waitFor(() => expect(stageEffectKeywords()).toContain('UnstoppableFunny'))
    },
    TEST_BUDGET_MS,
  )
})

describe('sanity', () => {
  it(
    'shows the low-morale effect of enemy 8627 beside its panic',
    async () => {
      setBattleMode('refractionRailway')
      await mountEnemy('8171')

      const lowMorale = await within(requireSlot('detail-sections')).findByText(
        '·Low Morale Effects',
      )
      const block = lowMorale.closest('[data-slot="low-morale-effect"]')
      expect(block?.textContent).toContain('(3 times per turn)')
      expect(within(requireSlot('detail-sections')).getByText('·Panic Effects')).toBeDefined()
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows only the panic description of enemy 8389, whose panic type is 9997, with no labels',
    async () => {
      await mountEnemy('8389')

      const desc = await waitFor(() => requireSlot('panic-desc'))
      await waitFor(() => expect(desc.textContent).toContain('Unobservable'))
      expect(desc.textContent).not.toContain('·')
      expect(desc.querySelector('[data-slot="low-morale-effect"]')).toBeNull()
    },
    TEST_BUDGET_MS,
  )
})

describe('client text styles', () => {
  it(
    'draws the OrangeMark clauses of passive 142302 as mark runs around their keyword chips',
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
        const found = [...card.querySelectorAll<HTMLElement>('.rt-mark')]
        expect(found.length).toBeGreaterThan(1)
        return found
      })
      const [first] = marks
      expect(first?.textContent).toContain('Gain 8')
      expect(first?.querySelector('button')).not.toBeNull()
      expect(card.textContent).not.toContain('<')
    },
    TEST_BUDGET_MS,
  )
})

function headerSprite(): HTMLImageElement {
  const image = within(requireSlot('detail-header')).getByTestId<HTMLImageElement>(
    'battle-detail-sprite-image',
  )
  return image
}

function headerBackground(): string | null {
  return within(requireSlot('detail-header'))
    .getByTestId('battle-detail-background')
    .getAttribute('src')
}

function growthInput(label: string): HTMLInputElement {
  const group = selectorGroup('Growth')
  if (group === null) throw new Error('no growth group')
  return within(group).getByLabelText<HTMLInputElement>(label)
}

function growthInputs(): HTMLInputElement[] {
  const group = selectorGroup('Growth')
  if (group === null) return []
  return within(group).getAllByRole<HTMLInputElement>('spinbutton')
}

function setGrowth(values: readonly number[]) {
  growthInputs().forEach((input, index) => {
    fireEvent.change(input, { target: { value: String(values[index]) } })
  })
}

function skillLevelOf(skillId: string): string | null | undefined {
  return requireSlot('detail-sections').querySelector(
    `[data-skill-id="${skillId}"] [data-slot="skill-level"]`,
  )?.textContent
}

const XICHUN_STAGE = 'storybattle-cultivation.json#911909'
const XICHUN_UNLOCK_STAGE = 'storybattle-cultivation.json#911911'

describe('Jia Xichun growth', () => {
  it(
    'shows no growth inputs on the rows of unit 400001',
    async () => {
      await mountAssistant('400001')

      expect(selectorGroup('Growth')).toBeNull()
      expect(renderedSkillIds()).toEqual(['40000101', '40000102', '40000103', '40000104'])
      expect(skillLevelOf('40000101')).toBe('50')
    },
    TEST_BUDGET_MS,
  )

  it(
    'labels three inputs 0 to 1000 by the track names and opens them at 1000 with the grown kit',
    async () => {
      await mountAssistant('400001', XICHUN_STAGE)

      const inputs = growthInputs()
      expect(inputs).toHaveLength(3)
      for (const input of inputs) {
        expect(input.value).toBe('1000')
        expect(input.min).toBe('0')
        expect(input.max).toBe('1000')
      }
      expect(growthInput('Zhi (智)')).toBe(inputs[0])
      expect(growthInput('Yong (勇)')).toBe(inputs[1])
      expect(growthInput('Ren (仁)')).toBe(inputs[2])
      expect(skillLevelOf('40000101')).toBe('67')
      expect(skillLevelOf('40000103')).toBe('70')
      expect(skillLevelOf('40000104')).toBe('64')
      expect(statCell('Defense')).toMatch(/^60\b/)
      expect(renderedSkillIds()).toContain('40000501')
      expect(renderedSkillIds()).not.toContain('40000502')
    },
    TEST_BUDGET_MS,
  )

  it(
    'drops to level 50, level-1 skills, no 40000501 and the base passives at 0/0/0',
    async () => {
      await mountAssistant('400001', XICHUN_UNLOCK_STAGE)

      setGrowth([0, 0, 0])

      await waitFor(() => expect(skillLevelOf('40000101')).toBe('50'))
      expect(statCell('Defense')).toMatch(/^50\b/)
      expect(renderedSkillIds()).not.toContain('40000501')
      expect(renderedSkillIds()).toContain('40000502')
      const strikeName = requireSlot('detail-sections').querySelector(
        '[data-skill-id="40000101"] [data-slot="name-plate"]',
      )?.textContent
      expect(strikeName).not.toMatch(/[2-5]/)
      const passives = renderedPassiveIds()
      expect(passives).toContain('40000103')
      expect(passives).toContain('940000501')
      expect(passives).not.toContain('940000512')
      expect(passives).not.toContain('940000520')
    },
    TEST_BUDGET_MS,
  )

  it.each([
    [[500, 500, 0], true],
    [[800, 0, 0], true],
    [[499, 1000, 0], false],
  ] as const)(
    'shows 40000501 at growth %j: %s',
    async (values, shown) => {
      await mountAssistant('400001', XICHUN_STAGE)

      setGrowth(values)

      await waitFor(() => expect(growthInputs()[0]?.value).toBe(String(values[0])))
      expect(renderedSkillIds().includes('40000501')).toBe(shown)
    },
    TEST_BUDGET_MS,
  )

  it(
    'writes the lesson-grade unlock of passive 940000501 at the sync-lock position and none on 40000101',
    async () => {
      await mountAssistant('400001', XICHUN_STAGE)

      const card = requireSlot('detail-sections').querySelector<HTMLElement>(
        '[data-passive-id="940000501"]',
      )
      if (card === null) throw new Error('no passive 940000501')
      const unlock = await within(card).findByText(/^Unlock: /)
      expect(unlock.getAttribute('data-slot')).toBe('passive-unlock')
      expect(unlock.style.right).toBe('0.4375rem')
      expect(unlock.style.bottom).toBe('0.4375rem')
      expect(
        requireSlot('detail-sections').querySelector(
          '[data-passive-id="40000101"] [data-slot="passive-unlock"]',
        ),
      ).toBeNull()
    },
    TEST_BUDGET_MS,
  )

  it(
    'names the lesson and grade of the 940000501 unlock in KR at 0/0/0, the third input 인(仁)',
    async () => {
      await i18n.changeLanguage('KR')
      await mountAssistant('400001', XICHUN_STAGE)

      const inputs = within(requireSlot('detail-selector')).getAllByRole<HTMLInputElement>(
        'spinbutton',
      )
      expect(within(requireSlot('detail-selector')).getByLabelText('인(仁)')).toBe(inputs[2])
      for (const input of inputs) fireEvent.change(input, { target: { value: '0' } })

      await waitFor(() => expect(inputs[0]?.value).toBe('0'))
      const unlock = requireSlot('detail-sections').querySelector<HTMLElement>(
        '[data-passive-id="940000501"] [data-slot="passive-unlock"]',
      )
      expect(unlock?.textContent).toBe(
        '해금 조건: 거울을 통해 본 자아와 세계의 형이상학적 탐구 고급',
      )
    },
    TEST_BUDGET_MS,
  )
})

describe('swapped skills', () => {
  it.each([
    ['enemy', '1059', 'battle-railway-dungeon-04.json#401', '860602'],
    ['enemy', '91005', 'storybattle1-3.json#10318', '9100606'],
  ] as const)(
    'lists the swap-in skill of %s card %s on %s as an ordinary card: %s',
    async (_family, id, stage, skillId) => {
      await mountEnemy(id, stage)

      expect(renderedSkillIds()).toContain(skillId)
      const card = requireSlot('detail-sections').querySelector(`[data-skill-id="${skillId}"]`)
      expect(card?.querySelector('[data-slot="skill-acquire"]')).toBeNull()
    },
    TEST_BUDGET_MS,
  )

  it(
    'lists 40000502 right after 40000501 on 400005 with no acquire line',
    async () => {
      await mountAssistant('400001', XICHUN_UNLOCK_STAGE)

      const skillIds = renderedSkillIds()
      expect(skillIds.indexOf('40000502')).toBe(skillIds.indexOf('40000501') + 1)
      expect(skillIds.indexOf('40000501')).toBeGreaterThan(0)
      const swapped = requireSlot('detail-sections').querySelector('[data-skill-id="40000502"]')
      const acquired = requireSlot('detail-sections').querySelector('[data-skill-id="40000501"]')
      expect(acquired?.querySelector('[data-slot="skill-acquire"]')).not.toBeNull()
      expect(swapped?.querySelector('[data-slot="skill-acquire"]')).toBeNull()
    },
    TEST_BUDGET_MS,
  )
})

const PWIE = '400041'
const pwieStage = (stageId: number) => `storybattle-a1c10p2.json#${String(stageId)}`

function formButtons(name = 'Form'): { label: string; pressed: boolean; disabled: boolean }[] {
  const group = selectorGroup(name)
  if (group === null) return []
  return within(group)
    .getAllByRole<HTMLButtonElement>('button')
    .map((button) => ({
      label: button.textContent ?? '',
      pressed: button.getAttribute('aria-pressed') === 'true',
      disabled: button.disabled,
    }))
}

describe('Pwie forms', () => {
  it.each([11051, 11042, 11028])(
    'locks row %i on the base form',
    async (stageId) => {
      await mountAssistant(PWIE, pwieStage(stageId))

      expect(formButtons()).toEqual([{ label: 'Base', pressed: true, disabled: true }])
    },
    TEST_BUDGET_MS,
  )

  it.each([
    [11030, 'Pink Mary Janes'],
    [11038, 'Golden Shroud'],
  ] as const)(
    'locks row %i on %s',
    async (stageId, label) => {
      await mountAssistant(PWIE, pwieStage(stageId))

      expect(formButtons()).toEqual([{ label, pressed: true, disabled: true }])
    },
    TEST_BUDGET_MS,
  )

  it.each([
    [11044, ['Base', 'Pink Mary Janes', 'Golden Shroud']],
    [11047, ['Base', 'Pink Mary Janes', 'Golden Shroud']],
    [11046, ['Base', 'Pink Mary Janes']],
    [11048, ['Base', 'Pink Mary Janes']],
    [11052, ['Pink Mary Janes', 'Golden Shroud']],
    [11034, ['Pink Mary Janes', 'Golden Shroud']],
  ] as const)(
    'enables the forms row %i can be fought in: %j',
    async (stageId, labels) => {
      await mountAssistant(PWIE, pwieStage(stageId))

      const buttons = formButtons()
      expect(buttons.map((button) => button.label)).toEqual(labels)
      expect(buttons.every((button) => !button.disabled)).toBe(true)
      expect(buttons[0]?.pressed).toBe(true)
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows no form buttons on the cocoon row 11031 and draws the cocoon at Lv1',
    async () => {
      await mountAssistant(PWIE, pwieStage(11031))

      expect(selectorGroup('Form')).toBeNull()
      expect(pressed('Story')).toMatch(/\(Lv\. 1\)/)
      expect(headerSprite().getAttribute('src')).toBe(
        getBattleSpritePath('assistant', '400042_Buffe_CocoonAppearance'),
      )
    },
    TEST_BUDGET_MS,
  )

  it(
    'switches the sprite and kit with the form on row 11044',
    async () => {
      await mountAssistant(PWIE, pwieStage(11044))

      expect(headerSprite().getAttribute('src')).toBe(
        getBattleSpritePath('assistant', '400041_LittleBuffeAppearance'),
      )

      selectIn('Form', 'Pink Mary Janes')
      await waitFor(() =>
        expect(headerSprite().getAttribute('src')).toBe(
          getBattleSpritePath('assistant', '400041_LittleBuffe_2pAppearance'),
        ),
      )

      selectIn('Form', 'Golden Shroud')
      await waitFor(() =>
        expect(headerSprite().getAttribute('src')).toBe(
          getBattleSpritePath('assistant', '400041_LittleBuffe_3pAppearance'),
        ),
      )
    },
    TEST_BUDGET_MS,
  )

  it(
    'reads the KR form names',
    async () => {
      await i18n.changeLanguage('KR')
      await mountAssistant(PWIE, pwieStage(11044))

      await waitFor(() =>
        expect(formButtons('형태').map((button) => button.label)).toEqual([
          '기본',
          '분홍색 메리 제인',
          '황금 수의',
        ]),
      )
    },
    TEST_BUDGET_MS,
  )

  it.each([
    [
      11030,
      ['Charge', 'BuffetShoes'],
      [
        ['충전', '3'],
        ['분홍색 메리 제인', ''],
      ],
    ],
    [
      11038,
      ['Charge', 'BuffetShoes', 'BuffetGoldenClothes'],
      [
        ['충전', '4'],
        ['분홍색 메리 제인', ''],
        ['황금 수의', ''],
      ],
    ],
  ] as const)(
    'lists the form start buffs of row %i first in the stage effects: %j',
    async (stageId, keywordIds, cards) => {
      await i18n.changeLanguage('KR')
      await mountAssistant(PWIE, pwieStage(stageId))

      await waitFor(() =>
        expect(stageEffectKeywords().slice(0, keywordIds.length)).toEqual(keywordIds),
      )
      const block = within(requireSlot('detail-selector')).getByTestId('battle-stage-effects')
      for (const [index, keywordId] of keywordIds.entries()) {
        const card = block.querySelector<HTMLElement>(`[data-keyword-id="${keywordId}"]`)
        const [name, stack] = cards[index] ?? []
        const title = card?.querySelector('.font-semibold')
        expect(title?.textContent).toBe(`${name ?? ''}${stack ?? ''}`)
        expect(card?.querySelector('img')?.getAttribute('src')).toBe(
          getBattleKeywordIconPath(keywordId),
        )
      }
      expect(stageEffectKeywords().filter((keywordId) => keywordId === 'Charge')).toHaveLength(1)
    },
    TEST_BUDGET_MS,
  )

  it.each([11051, 11031])(
    'lists no form start buff on row %i',
    async (stageId) => {
      await mountAssistant(PWIE, pwieStage(stageId))

      expect(stageEffectKeywords()).not.toContain('Charge')
      expect(stageEffectKeywords()).not.toContain('BuffetShoes')
      expect(stageEffectKeywords()).not.toContain('BuffetGoldenClothes')
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows Charge once first on row 11038 in CN with a title on every card',
    async () => {
      await i18n.changeLanguage('CN')
      await mountAssistant(PWIE, pwieStage(11038))

      await waitFor(() => expect(stageEffectKeywords()[0]).toBe('Charge'))
      const keywordIds = stageEffectKeywords()
      expect(keywordIds.filter((keywordId) => keywordId === 'Charge')).toHaveLength(1)
      expect(new Set(keywordIds).size).toBe(keywordIds.length)
      const block = within(requireSlot('detail-selector')).getByTestId('battle-stage-effects')
      const titles = [...block.querySelectorAll('[data-keyword-id]')].map(
        (card) => card.querySelector('.font-semibold')?.textContent ?? '',
      )
      expect(titles).toHaveLength(keywordIds.length)
      expect(titles.every((title) => title !== '')).toBe(true)
    },
    TEST_BUDGET_MS,
  )

  it(
    'labels every CN form on row 11044 with base first',
    async () => {
      await i18n.changeLanguage('CN')
      await mountAssistant(PWIE, pwieStage(11044))

      await waitFor(() => expect(formButtons('形态')).toHaveLength(3))
      const labels = formButtons('形态').map((button) => button.label)
      expect(labels[0]).toBe('基本')
      expect(labels.every((label) => label !== '')).toBe(true)
    },
    TEST_BUDGET_MS,
  )
})

describe('header sprite and map', () => {
  it.each([
    ['1321', 'abbattle1-4.json#10424', '8045_ElectricCentipedeAppearance'],
    ['8041', undefined, '8041_BrassBullAppearance'],
    ['1503', undefined, '1503_BlackThreadAppearance'],
    ['91005', 'storybattle1-3.json#10318', '91005_NCorpAppearance'],
    ['8106', 'mirrordungeon-abbattle5-0.json#2061527', '8106_Ahab_AwakenAppearance__phase1'],
    ['8106', 'battle-railway-dungeon-03.json#311', '8106_Ahab_AwakenAppearance__phase1'],
    ['8999', undefined, '8999_VergiliusKkomiAppearance'],
  ] as const)(
    'places the sprite file of card %s on %s by its measures: %s',
    async (id, stage, file) => {
      await mountEnemy(id, stage)

      const image = headerSprite()
      expect(image.getAttribute('src')).toBe(getBattleSpritePath('enemy', file))
      expect(image.style.position).toBe('absolute')
      expect(image.style.maxWidth).toBe('none')
      expect(image.classList).not.toContain('object-contain')
    },
    TEST_BUDGET_MS,
  )

  it(
    'draws the 400005 sprite file on its 8.5 rows',
    async () => {
      await mountAssistant('400001', XICHUN_STAGE)

      expect(headerSprite().getAttribute('src')).toBe(
        getBattleSpritePath('assistant', '400001_JiashichunAppearance'),
      )
    },
    TEST_BUDGET_MS,
  )

  it(
    'switches the Gas Harpoon sprite to pip, st and qu with phases 8106, 81062 and 81063',
    async () => {
      await mountEnemy('8106')

      expect(headerSprite().getAttribute('src')).toBe(
        getBattleSpritePath('enemy', '8106_Ahab_AwakenAppearance__phase1'),
      )
      selectIn('Phase', 'Phase 2')
      await waitFor(() =>
        expect(headerSprite().getAttribute('src')).toBe(
          getBattleSpritePath('enemy', '8106_Ahab_AwakenAppearance__phase2'),
        ),
      )
      selectIn('Phase', 'Phase 3')
      await waitFor(() =>
        expect(headerSprite().getAttribute('src')).toBe(
          getBattleSpritePath('enemy', '8106_Ahab_AwakenAppearance__phase3'),
        ),
      )
    },
    TEST_BUDGET_MS,
  )

  it(
    'draws the second Shedding Tears map behind Camellia phase 80442 and the row map behind 8044',
    async () => {
      await mountEnemy('8044')

      expect(headerBackground()).toBe(getStageBackgroundPath('Cp4_SheddingTears'))
      selectIn('Phase', 'Phase 2')
      await waitFor(() =>
        expect(headerBackground()).toBe(getStageBackgroundPath('Cp4_SheddingTears__second')),
      )
    },
    TEST_BUDGET_MS,
  )
})

describe('envy name tag', () => {
  it(
    'titles card 1110 in KR by the South Liu identity with the unit name above',
    async () => {
      await i18n.changeLanguage('KR')
      await mountEnemy('1110')

      const header = within(requireSlot('detail-header'))
      expect(await header.findByRole('heading', { level: 1 })).toHaveTextContent(
        '남부 리우 협회 4과',
      )
      expect(header.getByTestId('battle-abnormality-line').textContent).toBe('질투 죄종')
      expect(requireSlot('detail-header').textContent).not.toContain('료슈')
    },
    TEST_BUDGET_MS,
  )

  it.each([
    ['EN', 'Peccatulum Invidiae?', 'W Corp. L3 Cleanup Agent'],
    ['KR', '질투 죄종?', 'W사 3등급 정리 요원'],
  ] as const)(
    'titles card 1099 in %s by its identity with the unit name above',
    async (language, unitLine, title) => {
      await i18n.changeLanguage(language)
      await mountEnemy('1099')

      const header = within(requireSlot('detail-header'))
      expect(await header.findByRole('heading', { level: 1 })).toHaveTextContent(title)
      expect(header.getByTestId('battle-abnormality-line').textContent).toBe(unitLine)
      expect(requireSlot('detail-header').textContent).not.toContain('Don Quixote')
      expect(requireSlot('detail-header').textContent).not.toContain('돈키호테')
    },
    TEST_BUDGET_MS,
  )
})

describe('panic icon', () => {
  it.each([
    ['assistant', '9999', '9999'],
    ['enemy', '90004', '9999'],
  ] as const)(
    'draws the panic of %s card %s with the %s icon',
    async (family, id, icon) => {
      if (family === 'assistant') await mountAssistant(id)
      else await mountEnemy(id)

      const iconImage = await waitFor(() => requireSlot('panic-icon'))
      expect(iconImage.getAttribute('src')).toBe(getPanicIconPath(icon))
    },
    TEST_BUDGET_MS,
  )
})

describe('regenerated labels', () => {
  it(
    'reads story dungeon row 10412 of 8016 as 4-54 (Lv. 31)',
    async () => {
      await mountEnemy('8016')

      expect(groupButtons('Story')).toContain('4-54 (Lv. 31)')
    },
    TEST_BUDGET_MS,
  )

  it(
    'cuts the mirror event place of stage 2070512 to 20 code points and an ellipsis',
    async () => {
      await mountEnemy('8630')

      expect(groupButtons('MD')).toContain("'This is a warzone.'… (Lv. 60)")
    },
    TEST_BUDGET_MS,
  )

  it(
    'opens 1126 on the row its stage search names',
    async () => {
      await mountEnemy('1126', 'abbattle-a1c8p2.json#10827')

      expect(pressed('Story')).toMatch(/^8-/)
      expect(renderedSkillIds().every((skillId) => skillId.startsWith('1133'))).toBe(true)
    },
    TEST_BUDGET_MS,
  )
})
