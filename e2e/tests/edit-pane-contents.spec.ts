import { randomUUID } from 'node:crypto'
import type { Locator } from '@playwright/test'
import startBuffSpec from '@static/data/MD7/startBuffs.json'
import startBuffNames from '@static/i18n/EN/MD7/startBuffs.json'
import startGiftPools from '@static/data/MD7/startEgoGiftPools.json'
import { PLANNER_CONFIG } from '@/lib/constants'
import { test, expect } from '../src/browser'
import { seedLocalPlanner } from '../src/localPlanner'

// Each edit-page pane loads its own static data behind its own Suspense boundary, so a loader
// that fails or skips validation renders an empty pane while the rest of the page looks healthy.
// This sweep opens every pane and requires proof of real content in each.

// The data assertions read the version's shipped JSON, so they follow a data update but not a
// version bump — the guard in the test turns that into a named failure.
const MD_VERSION = 7
const SINNER_COUNT = 12
const GIFTS_PER_KEYWORD_ROW = 3
const IDENTITY_CARD_ALT = 'Sinner background'
const BASE_BUFF_LEVEL = 1

// The pane shows one card per unenhanced buff, so the level-1 entries are its expected contents.
const baseBuffNames = Object.entries(
  startBuffSpec as Record<string, { level: number; localizeId: string }>,
)
  .filter(([, spec]) => spec.level === BASE_BUFF_LEVEL)
  .map(([id, spec]) => {
    const name = (startBuffNames as Record<string, string>)[spec.localizeId]
    if (name === undefined) {
      throw new Error(`MD${String(MD_VERSION)} i18n carries no name for buff ${id}`)
    }
    return name
  })

const giftKeywords = Object.keys(startGiftPools)

interface PaneSpec {
  /** PlannerSection heading the pane lives under. */
  section: string
  /** Accessible name of the opener button; the bare summary body otherwise. */
  openerButton?: string
  /** DialogTitle expected once open. */
  dialogTitle: string
  /** Proof the pane rendered real game data rather than an empty container. */
  expectContent: (dialog: Locator) => Promise<void>
}

const PANES: PaneSpec[] = [
  {
    section: 'Deck Builder',
    openerButton: 'Edit Deck',
    dialogTitle: 'Edit Deck',
    // The pane is a catalog of every selectable identity and EGO. The card count is a floor
    // rather than a total, since the grid reveals its rows as they scroll in.
    expectContent: async (dialog) => {
      await expect(dialog.getByRole('button', { name: 'Identity', exact: true })).toBeVisible()
      await expect(dialog.getByRole('button', { name: 'E.G.O', exact: true })).toBeVisible()
      expect(
        await dialog.getByRole('img', { name: IDENTITY_CARD_ALT, exact: true }).count(),
      ).toBeGreaterThan(SINNER_COUNT)
    },
  },
  {
    section: 'Grace of Stars',
    dialogTitle: 'Grace of Stars',
    // Names come from the i18n file and ids from the spec file, so asserting every base buff by
    // name covers the merge of both — the empty-grid regression showed the pane with neither.
    expectContent: async (dialog) => {
      for (const name of baseBuffNames) {
        await expect(dialog.getByRole('button', { name, exact: true })).toBeVisible()
      }
    },
  },
  {
    section: 'Start E.G.O Gifts',
    dialogTitle: 'Start E.G.O Gifts',
    expectContent: async (dialog) => {
      for (const keyword of giftKeywords) {
        await expect(dialog.getByRole('button', { name: keyword, exact: true })).toBeVisible()
      }
      await expect(dialog.getByRole('img', { name: /^EGO Gift \d+$/ })).toHaveCount(
        giftKeywords.length * GIFTS_PER_KEYWORD_ROW,
      )
    },
  },
  {
    section: 'E.G.O Gift Observation',
    dialogTitle: 'E.G.O Gift Observation',
    // The list pages its gifts in, so the floor is one screen's worth rather than the full pool.
    expectContent: async (dialog) => {
      const gifts = dialog.getByRole('img', { name: /^EGO Gift \d+$/ })
      await expect(gifts.first()).toBeVisible()
      expect(await gifts.count()).toBeGreaterThan(GIFTS_PER_KEYWORD_ROW)
    },
  },
]

test('every edit-page pane opens and shows its data', async ({ page }) => {
  expect(
    PLANNER_CONFIG.mdCurrentVersion,
    'the editor moved to another MD version; repoint this spec at its data files',
  ).toBe(MD_VERSION)

  const plannerId = randomUUID()
  await seedLocalPlanner(page, { plannerId, title: 'pane sweep' })

  await page.goto(`/planner/md/${plannerId}/edit`, { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { name: 'Closing Notes' })).toBeVisible({
    timeout: 20_000,
  })

  for (const pane of PANES) {
    const section = page.locator('section', {
      has: page.getByRole('heading', { name: pane.section, exact: true }),
    })

    if (pane.openerButton !== undefined) {
      await section.getByRole('button', { name: pane.openerButton }).click()
    } else {
      await section.locator('button.selectable').first().click()
    }

    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog.getByRole('heading', { name: pane.dialogTitle, exact: true })).toBeVisible()

    await pane.expectContent(dialog)

    await page.keyboard.press('Escape')
    await expect(dialog).not.toBeVisible()
  }
})
