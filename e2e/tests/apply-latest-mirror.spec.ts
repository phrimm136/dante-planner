import { test, expect } from '../src/browser'
import type { APIRequestContext } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { PLANNER_CONFIG } from '@/lib/constants'
import { authenticateContext, createAuthenticatedApi } from '../src/auth'
import { seedLocalPlanner } from '../src/localPlanner'
import { browserPlannerPayload } from '../src/plannerContent'
import { closeSeedPool, createUser, deleteUser, sql, type SeededUser } from '../src/seed'

// The apply-latest-mirror dialog promises "your selections will not change". On the community
// detail page the owner's action object is the SERVER-derived copy, so applying writes that copy
// over the local IndexedDB row — local draft edits included. This spec asserts the promise the
// dialog makes: the draft survives the upgrade.

test.describe.configure({ timeout: 60_000 })

test.afterAll(closeSeedPool)

test('applying the latest mirror from the community page keeps local draft edits', async ({
  page,
  context,
  baseURL,
}) => {
  const user: SeededUser = await createUser('mirror-clobber')
  const api: APIRequestContext = await createAuthenticatedApi(baseURL!, user.id)
  const plannerId = randomUUID()
  const serverTitle = `e2e mirror-clobber ${plannerId.slice(0, 8)}`
  const draftTitle = `${serverTitle} draft edits`
  const staleMirror = PLANNER_CONFIG.mdCurrentVersion - 1

  try {
    const settings = await api.put('/api/user/settings', { data: { syncEnabled: true } })
    expect(settings.status(), await settings.text()).toBe(200)

    const created = await api.put(`/api/planner/md/${plannerId}`, {
      data: { ...browserPlannerPayload(plannerId, serverTitle), syncVersion: 1 },
    })
    expect([200, 201], await created.text()).toContain(created.status())
    const published = await api.put(`/api/planner/md/${plannerId}/publish`, {
      data: { published: true },
    })
    expect(published.status(), await published.text()).toBe(200)

    // The server validates submitted content against the game data of its stated version, so a
    // stale mirror cannot be created through the API; the row is aged by seeding instead —
    // exactly the state a real planner reaches when a new mirror ships under it.
    await sql('UPDATE planner_content SET game_content_version = ? WHERE planner_id = UUID_TO_BIN(?)', [
      staleMirror,
      plannerId,
    ])

    await authenticateContext(context, user.id, baseURL!)
    await seedLocalPlanner(page, {
      plannerId,
      title: draftTitle,
      status: 'draft',
      contentVersion: staleMirror,
      published: true,
    })

    await page.goto(`/planner/md/gesellschaft/${plannerId}`, { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('button', { name: 'Apply Latest Mirror' })).toBeVisible({
      timeout: 20_000,
    })
    await page.getByRole('button', { name: 'Apply Latest Mirror' }).click()
    await page.getByRole('button', { name: 'Apply', exact: true }).click()
    await expect(page.getByText('Plan updated to the latest Mirror Dungeon')).toBeVisible({
      timeout: 15_000,
    })

    // The promise under test: the upgrade moved the game-data version, not the user's work.
    await page.goto(`/planner/md/${plannerId}/edit`, { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: 'Closing Notes' })).toBeVisible({
      timeout: 20_000,
    })
    // The title renders as an input's value, which text locators never match.
    await expect(
      page.locator('input').first(),
      'the local draft was clobbered by the server copy',
    ).toHaveValue(draftTitle)
  } finally {
    await api.delete(`/api/planner/md/${plannerId}`)
    await api.dispose()
    await deleteUser(user)
  }
})
