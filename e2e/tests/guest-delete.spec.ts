import { test, expect } from '../src/browser'
import { randomUUID } from 'node:crypto'
import { authenticateContext } from '../src/auth'
import { producesRequest } from '../src/gestures'
import { seedLocalPlanner, readPlannerRows } from '../src/localPlanner'
import { dropPlanner, seedPlanner } from '../src/plannerFixture'
import { closeSeedPool } from '../src/seed'
import type { Page } from '@playwright/test'

const GENERIC_TOAST = 'We encountered an unexpected error'

test.afterAll(closeSeedPool)

async function openDetail(page: Page, plannerId: string) {
  await page.goto(`/planner/md/${plannerId}`, { waitUntil: 'domcontentloaded' })
  await expect(page.locator('#seo-skeleton')).toHaveCount(0, { timeout: 20_000 })
  await expect(page.getByRole('button', { name: 'Delete' })).toBeVisible({ timeout: 20_000 })
}

/** Confirms the delete and asserts the DELETE statuses the page received on the way out. */
async function confirmDelete(page: Page, expectedStatuses: number[]) {
  const statuses: number[] = []
  page.on('response', (response) => {
    if (response.request().method() === 'DELETE') statuses.push(response.status())
  })

  await page.getByRole('button', { name: 'Delete' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.waitForURL('**/planner/md', { timeout: 20_000 })
  await expect(page.getByText(GENERIC_TOAST)).toHaveCount(0)
  expect(statuses).toEqual(expectedStatuses)
}

function tombstoneRows(rows: string[], plannerId: string): string[] {
  return rows.filter((row) => row.includes('"deletedAt"') && row.includes(plannerId))
}

test('a guest delete writes a tombstone and sends no request', async ({ page }) => {
  const plannerId = randomUUID()
  await seedLocalPlanner(page, { plannerId, title: `e2e guest draft ${plannerId.slice(0, 8)}`, status: 'draft' })

  await openDetail(page, plannerId)
  await confirmDelete(page, [])

  expect(tombstoneRows(await readPlannerRows(page), plannerId)).toHaveLength(1)
})

// The local copy arrives through the pull pass: a seed script re-runs on every navigation and
// would re-insert the row the deletion removed.
test('a guest tombstone is swept when the session returns', async ({ page, context, baseURL }) => {
  const fixture = await seedPlanner(baseURL!, 'guest-tombstone')
  try {
    await authenticateContext(context, fixture.user.id, baseURL!)
    await page.goto('/planner/md', { waitUntil: 'domcontentloaded' })
    await expect(page.getByText(fixture.title)).toBeVisible({ timeout: 30_000 })

    await context.clearCookies()
    await openDetail(page, fixture.plannerId)
    await confirmDelete(page, [])

    const afterDelete = await readPlannerRows(page)
    expect(tombstoneRows(afterDelete, fixture.plannerId)).toHaveLength(1)
    expect(afterDelete.some((row) => row.includes(`"title":"${fixture.title}"`))).toBe(false)

    await authenticateContext(context, fixture.user.id, baseURL!)
    await producesRequest(
      page,
      { method: 'DELETE', url: fixture.plannerId },
      () => page.goto('/planner/md', { waitUntil: 'domcontentloaded' }),
      { timeout: 30_000 },
    )

    await expect
      .poll(async () => (await fixture.api.get(`/api/planner/md/${fixture.plannerId}`)).status(), {
        timeout: 20_000,
      })
      .toBe(404)
    await expect
      .poll(async () => (await readPlannerRows(page)).filter((row) => row.includes(fixture.plannerId)))
      .toEqual([])
  } finally {
    await dropPlanner(fixture)
  }
})

test('a 401 delete ends in a tombstone', async ({ page, context, baseURL }) => {
  const fixture = await seedPlanner(baseURL!, 'lapsed-session')
  try {
    await authenticateContext(context, fixture.user.id, baseURL!)
    await page.goto('/planner/md', { waitUntil: 'domcontentloaded' })
    await expect(page.getByText(fixture.title)).toBeVisible({ timeout: 30_000 })
    await openDetail(page, fixture.plannerId)

    await context.clearCookies()
    await confirmDelete(page, [401])

    expect(tombstoneRows(await readPlannerRows(page), fixture.plannerId)).toHaveLength(1)
  } finally {
    await dropPlanner(fixture)
  }
})
