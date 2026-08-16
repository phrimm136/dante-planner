import { test, expect } from '../src/browser'
import { randomUUID } from 'node:crypto'
import { authenticateContext } from '../src/auth'
import { seedLocalPlanner } from '../src/localPlanner'
import { dropPlanner, seedPlanner } from '../src/plannerFixture'
import { closeSeedPool } from '../src/seed'

// The second device: a planner that exists only on the server must reach a browser that has
// never held it. The pull pass is the only thing standing between this user and a not-found
// page, because the edit route reads IndexedDB with no server fallback — a fresh context IS a
// second device, so nothing here seeds local storage.

test.afterAll(closeSeedPool)

test('a synced planner reaches a device that never held it, list and editor both', async ({
  page,
  context,
  baseURL,
}) => {
  const fixture = await seedPlanner(baseURL!, 'second-device')

  try {
    await authenticateContext(context, fixture.user.id, baseURL!)

    await page.goto('/planner/md', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('#seo-skeleton')).toHaveCount(0, { timeout: 20_000 })
    // The list renders IndexedDB rows, so this title being visible is the pull pass having
    // written the server copy into local storage — not a server-side listing.
    await expect(page.getByText(fixture.title)).toBeVisible({ timeout: 30_000 })

    // The edit route dereferences IndexedDB only; without the pulled row this is the
    // not-found page.
    await page.goto(`/planner/md/${fixture.plannerId}/edit`, { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: 'Closing Notes' })).toBeVisible({
      timeout: 20_000,
    })
  } finally {
    await dropPlanner(fixture)
  }
})

test('a deletion on another device purges the local copy through its tombstone', async ({
  page,
  context,
  baseURL,
}) => {
  const fixture = await seedPlanner(baseURL!, 'tombstone-purge')
  // The discriminating row: a planner the server never saw. Purging on tombstones keeps it;
  // the retired absence heuristic would have destroyed the only copy.
  const keeperId = randomUUID()
  const keeperTitle = `e2e keeper ${keeperId.slice(0, 8)}`

  try {
    await authenticateContext(context, fixture.user.id, baseURL!)
    await seedLocalPlanner(page, { plannerId: keeperId, title: keeperTitle })

    await page.goto('/planner/md', { waitUntil: 'domcontentloaded' })
    await expect(page.getByText(fixture.title)).toBeVisible({ timeout: 30_000 })
    await expect(page.getByText(keeperTitle)).toBeVisible()

    // The other device deletes; this one still holds the pulled IndexedDB copy.
    const deleted = await fixture.api.delete(`/api/planner/md/${fixture.plannerId}`)
    expect(deleted.status(), await deleted.text()).toBe(204)

    // A fresh mount re-runs the sync pass: the list renders the stale local row first, and the
    // tombstone the listing now carries is what takes it away.
    await page.reload({ waitUntil: 'domcontentloaded' })
    await expect(page.locator('#seo-skeleton')).toHaveCount(0, { timeout: 20_000 })
    await expect(page.getByText(fixture.title)).toBeHidden({ timeout: 30_000 })
    await expect(page.getByText(keeperTitle), 'the never-uploaded row was purged').toBeVisible()
  } finally {
    await dropPlanner(fixture)
  }
})
