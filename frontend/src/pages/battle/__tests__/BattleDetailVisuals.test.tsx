import { describe, it, expect, beforeEach } from 'vitest'
import { waitFor, within } from '@testing-library/react'
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
    'draws no separator in the skill list of enemy group 8063 and no rule under any section title',
    async () => {
      await mountBattle('enemy', '8063')

      const sections = requireSlot('detail-sections')
      expect(sections.querySelectorAll('[data-slot="skill-group-separator"]')).toHaveLength(0)
      expect(sections.querySelector('.divide-y')).toBeNull()
      for (const title of sections.querySelectorAll('h2')) {
        expect(title.nextElementSibling?.getAttribute('data-slot')).not.toBe('separator')
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
      const chip = await within(sanity).findByRole('button', { name: 'Attack Power Down' })
      expect(chip.closest('div')?.textContent).toContain('Turn Start: Gain 4')
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
