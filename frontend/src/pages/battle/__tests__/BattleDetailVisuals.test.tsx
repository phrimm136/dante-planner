import { describe, it, expect, beforeEach } from 'vitest'
import { fireEvent, waitFor, within } from '@testing-library/react'
import type { ReactNode } from 'react'

import i18n from '@/lib/i18n'
import { getEGORankIconPath, getSinnerIconPath } from '@/shared/assets'
import { SinnerScopedIdSchema, getSinnerFromId } from '@/shared/gameData'
import { mountRoute } from '@/test-utils/mountRoute'
import AssistantDetailPage from '../AssistantDetailPage'
import EnemyDetailPage from '../EnemyDetailPage'
import SinnerEventDetailPage from '../SinnerEventDetailPage'
import { BATTLE_DETAIL_ROUTES, type BattleFamily } from '../lib/battleVocabulary'

const LOAD_WAIT_MS = 10000
const TEST_BUDGET_MS = 30000

const PAGES: Record<BattleFamily, () => ReactNode> = {
  enemy: EnemyDetailPage,
  assistant: AssistantDetailPage,
  sinnerEvent: SinnerEventDetailPage,
}

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

function requireSlot(name: string): HTMLElement {
  const element = document.querySelector<HTMLElement>(`[data-slot="${name}"]`)
  if (element === null) throw new Error(`no ${name} slot rendered`)
  return element
}

async function mountBattle(family: BattleFamily, id: string) {
  const path = BATTLE_DETAIL_ROUTES[family]
  mountRoute(path, path.replace('$id', id), PAGES[family])
  await waitFor(() => expect(requireSlot('detail-sections')).toBeDefined(), {
    timeout: LOAD_WAIT_MS,
  })
  await waitFor(() => expect(document.querySelectorAll('[data-slot="skeleton"]').length).toBe(0), {
    timeout: LOAD_WAIT_MS,
  })
}

function section(name: string): HTMLElement {
  const found = within(requireSlot('detail-sections'))
    .getAllByRole('region')
    .find((region) => within(region).queryByRole('heading', { level: 2, name }))
  if (found === undefined) throw new Error(`no ${name} section`)
  return found
}

function paneOf(region: HTMLElement): HTMLElement {
  const pane = region.querySelector<HTMLElement>(':scope > h2 + div')
  if (pane === null) throw new Error('no pane under the section title')
  return pane
}

function expectOnlyBetweenBoxes(pane: HTMLElement, slot: string) {
  expect(pane.firstElementChild?.getAttribute('data-slot')).not.toBe(slot)
  expect(pane.lastElementChild?.getAttribute('data-slot')).not.toBe(slot)
  for (const separator of pane.querySelectorAll(`[data-slot="${slot}"]`)) {
    expect(separator.nextElementSibling?.getAttribute('data-slot')).not.toBe(slot)
  }
}

function sinnerIconOf(identityId: string): string {
  return getSinnerIconPath(getSinnerFromId(SinnerScopedIdSchema.parse(identityId)))
}

function headerImageSources(): (string | null)[] {
  return [...requireSlot('detail-header').querySelectorAll('img')].map((img) =>
    img.getAttribute('src'),
  )
}

describe('battle detail visuals', () => {
  it(
    'separates every pair of skill boxes of enemy group 8063 with a 5-unit line and 15 units on each side, and puts no rule under any section title',
    async () => {
      await mountBattle('enemy', '8063')

      const pane = paneOf(section('Skills'))
      const cards = pane.querySelectorAll('[data-skill-id]')
      const separators = [...pane.querySelectorAll<HTMLElement>('[data-slot="skill-separator"]')]
      expect(cards.length).toBeGreaterThan(1)
      expect(separators).toHaveLength(cards.length - 1)
      for (const separator of separators) {
        expect(separator.style.height).toBe('0.1094rem')
        expect(separator.style.marginBlock).toBe('0.3281rem')
      }
      expectOnlyBetweenBoxes(pane, 'skill-separator')
      expect(pane.style.paddingBottom).toBe('')

      const sections = requireSlot('detail-sections')
      expect(sections.querySelector('.divide-y')).toBeNull()
      for (const title of sections.querySelectorAll('h2')) {
        expect(title.nextElementSibling?.getAttribute('data-slot')).not.toBe('separator')
      }
    },
    TEST_BUDGET_MS,
  )

  it(
    'frames the passive pane of assistant 400040 at 15 units above and below with no inline padding, and draws a 5-unit line in the middle of each 80-unit gap',
    async () => {
      await mountBattle('assistant', '400040')

      const pane = paneOf(section('Passives'))
      expect(pane.style.padding).toBe('0.3281rem 0px')
      const separators = [...pane.querySelectorAll<HTMLElement>('[data-slot="passive-separator"]')]
      expect(separators).toHaveLength(pane.querySelectorAll('[data-passive-id]').length - 1)
      for (const separator of separators) {
        expect(separator.style.height).toBe('0.1094rem')
        expect(separator.style.marginBlock).toBe('0.8203rem')
      }
      expectOnlyBetweenBoxes(pane, 'passive-separator')
    },
    TEST_BUDGET_MS,
  )

  it(
    'places the battle and support passive headers of event sinner 10101 at their client offsets with no line beside either',
    async () => {
      await mountBattle('sinnerEvent', '10101')

      const headers = [
        ...section('Passives').querySelectorAll<HTMLElement>('[data-slot="passive-header"]'),
      ]
      expect(headers).toHaveLength(2)
      const [battle, support] = headers as [HTMLElement, HTMLElement]
      expect(battle.style.marginLeft).toBe('0.3281rem')
      expect(battle.style.width).toBe('8.2906rem')
      expect(battle.style.height).toBe('1.7281rem')
      expect(battle.style.fontSize).toBe('0.9844rem')
      expect(battle.style.marginBottom).toBe('0.875rem')
      expect(support.style.marginTop).toBe('1.75rem')
      for (const header of headers) {
        expect(header.previousElementSibling?.getAttribute('data-slot')).not.toBe(
          'passive-separator',
        )
        expect(header.nextElementSibling?.getAttribute('data-slot')).not.toBe('passive-separator')
      }
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows no sinner mark on enemy 8653, an identity copy',
    async () => {
      await mountBattle('enemy', '8653')

      expect(headerImageSources()).not.toContain(sinnerIconOf('10611'))
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows the sinner mark on event sinner 10101',
    async () => {
      await mountBattle('sinnerEvent', '10101')

      expect(headerImageSources()).toContain(sinnerIconOf('10101'))
    },
    TEST_BUDGET_MS,
  )

  it(
    'titles the 20601 E.G.O card of Peccatulum Invidiae 1112 with the ZAYIN plate',
    async () => {
      await mountBattle('enemy', '1112')

      const ego = section('E.G.O')
      const block = await waitFor(() => {
        const found = ego.querySelector<HTMLElement>('[data-ego-id="20601"]')
        if (found === null) throw new Error('no 20601 block')
        return found
      })
      const plate = within(block).getByRole('img', { name: 'ZAYIN rank' })
      expect(plate.getAttribute('src')).toBe(getEGORankIconPath('ZAYIN'))
      expect(within(block).queryByRole('heading', { level: 3 })).toBeNull()
    },
    TEST_BUDGET_MS,
  )

  it(
    'reads the panic of enemy 70082 from its own text with the Reduction keyword chip',
    async () => {
      await mountBattle('enemy', '70082')

      const sanity = section('Sanity')
      expect(await within(sanity).findByText('Atrophied')).toBeDefined()
      const chips = await within(sanity).findAllByRole('button', { name: 'Attack Power Down' })
      expect(
        chips.some((chip) => chip.closest('div')?.textContent?.includes('Turn Start: Gain 4')),
      ).toBe(true)
      expect(sanity.textContent).not.toContain('[Reduction]')
      const html = document.body.innerHTML
      expect(html).not.toMatch(/&lt;sprite|<sprite|&lt;link|<link/)
    },
    TEST_BUDGET_MS,
  )

  it(
    'hides passive 9999994 on assistant 400040 and lists its LCD association',
    async () => {
      await mountBattle('assistant', '400040')

      const passiveIds = [...section('Passives').querySelectorAll('[data-passive-id]')].map(
        (card) => card.getAttribute('data-passive-id'),
      )
      expect(passiveIds).toEqual(['40004001', '40004002', '40004003', '40004004', '40004005'])
      expect(
        await within(requireSlot('detail-metadata')).findByText(
          'LCD',
          {},
          { timeout: LOAD_WAIT_MS },
        ),
      ).toBeDefined()
    },
    TEST_BUDGET_MS,
  )
})

function triangleRight(skillId: string): string {
  const card = requireSlot('detail-sections').querySelector<HTMLElement>(
    `[data-skill-id="${skillId}"]`,
  )
  if (card === null) throw new Error(`no skill card ${skillId}`)
  const decoration = card.parentElement?.querySelector<HTMLElement>(
    ':scope > [data-slot="importance-decoration"]',
  )
  if (decoration == null) throw new Error(`skill ${skillId} is not decorated`)
  return decoration.style.getPropertyValue('--importance-icon-right')
}

function selectPart(label: string) {
  const group = within(requireSlot('detail-selector')).getByRole('group', { name: 'Part' })
  fireEvent.click(within(group).getByRole('button', { name: label }))
}

describe('skill triangle position by unit owner', () => {
  it(
    'places the triangle of skill 151905 on abnormality 1519 at the base position, 286 units in',
    async () => {
      await mountBattle('enemy', '1519')

      expect(triangleRight('151905')).toBe('6.2562rem')
    },
    TEST_BUDGET_MS,
  )

  it(
    'places the triangle of skill 9106603 on enemy 91068 at the unlock position, 86 units in',
    async () => {
      await mountBattle('enemy', '91068')

      expect(triangleRight('9106603')).toBe('1.8813rem')
    },
    TEST_BUDGET_MS,
  )

  it(
    'moves the triangle of abnormality 1307 from the base position under All to the unlock position once Roots is selected',
    async () => {
      await mountBattle('enemy', '1307')

      expect(triangleRight('130711')).toBe('6.2562rem')

      selectPart('Roots')

      await waitFor(() => expect(triangleRight('130711')).toBe('1.8813rem'))
    },
    TEST_BUDGET_MS,
  )

  it(
    'places the triangle of skill 40003804 on assistant 400038 at the unlock position',
    async () => {
      await mountBattle('assistant', '400038')

      expect(triangleRight('40003804')).toBe('1.8813rem')
    },
    TEST_BUDGET_MS,
  )
})
