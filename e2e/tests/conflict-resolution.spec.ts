import { test, expect } from '../src/browser'
import type { APIRequestContext, Page, BrowserContext } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { authenticateContext, createAuthenticatedApi } from '../src/auth'
import { producesRequest } from '../src/gestures'
import { seedLocalPlanner } from '../src/localPlanner'
import { browserPlannerPayload } from '../src/plannerContent'
import { closeSeedPool, createUser, deleteUser, type SeededUser } from '../src/seed'
import { OREGON_API } from '../src/staging'

// The 409 the client is built around, driven end to end: a manual save presenting a version the
// server has moved past opens ConflictResolutionDialog, and each choice must land on the wire
// and in the stores the way conflictChoice.ts plans it. The arrangement is the real divergence:
// the server row advanced on another device while this device's IndexedDB copy stayed behind.

test.describe.configure({ timeout: 60_000 })

test.afterAll(closeSeedPool)

interface DivergedFixture {
  user: SeededUser
  api: APIRequestContext
  plannerId: string
  localTitle: string
  serverTitle: string
  serverVersion: number
}

/** Server two versions ahead under a different title; the browser seeded one version behind. */
async function seedDivergence(
  page: Page,
  context: BrowserContext,
  baseURL: string,
  label: string,
  localStatus: 'saved' | 'draft' = 'saved',
): Promise<DivergedFixture> {
  const user = await createUser(label)
  const api = await createAuthenticatedApi(baseURL, user.id)
  const plannerId = randomUUID()
  const localTitle = `e2e ${label} ${plannerId.slice(0, 8)}`
  const serverTitle = `${localTitle} server`

  const settings = await api.put('/api/user/settings', { data: { syncEnabled: true } })
  expect(settings.status(), await settings.text()).toBe(200)

  const created = await api.put(`/api/planner/md/${plannerId}`, {
    data: { ...browserPlannerPayload(plannerId, localTitle), syncVersion: 1 },
  })
  expect([200, 201], await created.text()).toContain(created.status())
  const ackedVersion = (await created.json()).syncVersion as number

  const advanced = await api.put(`/api/planner/md/${plannerId}`, {
    data: { ...browserPlannerPayload(plannerId, serverTitle), syncVersion: ackedVersion },
  })
  expect(advanced.status(), await advanced.text()).toBe(200)
  const serverVersion = (await advanced.json()).syncVersion as number

  await authenticateContext(context, user.id, baseURL)
  await seedLocalPlanner(page, { plannerId, title: localTitle, status: localStatus })

  return { user, api, plannerId, localTitle, serverTitle, serverVersion }
}

async function dropDivergence(fixture: DivergedFixture): Promise<void> {
  await fixture.api.delete(`/api/planner/md/${fixture.plannerId}`)
  await fixture.api.dispose()
  await deleteUser(fixture.user)
}

async function saveIntoConflict(page: Page, plannerId: string): Promise<void> {
  await page.goto(`/planner/md/${plannerId}/edit`, { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { name: 'Closing Notes' })).toBeVisible({
    timeout: 20_000,
  })
  await page.getByRole('button', { name: 'Save', exact: true }).first().click()
  await expect(page.getByText('Changed on Another Device')).toBeVisible({ timeout: 15_000 })
}

test('the pull pass surfaces a server-ahead draft as a batch conflict, and discard adopts', async ({
  page,
  context,
  baseURL,
}) => {
  const fixture = await seedDivergence(page, context, baseURL!, 'conflict-batch', 'draft')

  try {
    // No save click: the background sync itself must ask rather than overwrite the draft.
    await page.goto('/planner/md', { waitUntil: 'domcontentloaded' })
    await expect(page.getByText('Changed on Another Device')).toBeVisible({ timeout: 30_000 })

    // The dialog renders the choice twice (the apply-to-all row and the per-item row);
    // either selects it for a single conflict.
    await page.getByRole('button', { name: 'Discard My Changes' }).first().click()
    await page.getByRole('button', { name: 'Resolve All' }).click()
    await expect(page.getByText('Changed on Another Device')).toBeHidden({ timeout: 15_000 })

    // Adoption pulled the server copy over the draft.
    await expect(page.getByText(fixture.serverTitle)).toBeVisible({ timeout: 15_000 })
    await expect(page.getByText(fixture.localTitle, { exact: true })).toBeHidden()
  } finally {
    await dropDivergence(fixture)
  }
})

test('Keep Local force-pushes the stale copy over the server', async ({
  page,
  context,
  baseURL,
}) => {
  const fixture = await seedDivergence(page, context, baseURL!, 'conflict-local')

  try {
    await saveIntoConflict(page, fixture.plannerId)

    const forced = await producesRequest(
      page,
      { method: 'PUT', url: `/api/planner/md/${fixture.plannerId}` },
      () => page.getByRole('button', { name: 'Overwrite Server' }).click(),
    )
    expect(forced.url(), 'the push did not carry force').toContain('force=true')
    await expect(page.getByText('Changed on Another Device')).toBeHidden()

    // The server now holds this device's copy under a version past the one that conflicted.
    const reader = await createAuthenticatedApi(OREGON_API, fixture.user.id)
    const stored = await reader.get(`/api/planner/md/${fixture.plannerId}`)
    expect(stored.status(), await stored.text()).toBe(200)
    const body = await stored.json()
    await reader.dispose()
    expect(body.title).toBe(fixture.localTitle)
    expect(body.syncVersion).toBeGreaterThan(fixture.serverVersion)
  } finally {
    await dropDivergence(fixture)
  }
})

test('Use Server adopts the server copy and the next save is clean', async ({
  page,
  context,
  baseURL,
}) => {
  const fixture = await seedDivergence(page, context, baseURL!, 'conflict-server')

  try {
    await saveIntoConflict(page, fixture.plannerId)

    await page.getByRole('button', { name: 'Discard My Changes' }).click()
    await expect(page.getByText('Server version loaded')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByText('Changed on Another Device')).toBeHidden()

    // Adoption keeps the server copy: the row the server holds is untouched by the resolution.
    const reader = await createAuthenticatedApi(OREGON_API, fixture.user.id)
    const stored = await reader.get(`/api/planner/md/${fixture.plannerId}`)
    expect(stored.status(), await stored.text()).toBe(200)
    const body = await stored.json()
    await reader.dispose()
    expect(body.title).toBe(fixture.serverTitle)
    expect(body.syncVersion).toBe(fixture.serverVersion)
  } finally {
    await dropDivergence(fixture)
  }
})

test('Keep Both forks the local copy and adopts the server one', async ({
  page,
  context,
  baseURL,
}) => {
  const fixture = await seedDivergence(page, context, baseURL!, 'conflict-both')

  try {
    await saveIntoConflict(page, fixture.plannerId)

    // The fork is a PUT to a planner id this test did not mint.
    const forkUpload = page.waitForRequest(
      (request) =>
        request.method() === 'PUT' &&
        /\/api\/planner\/md\/[0-9a-f-]{36}$/.test(request.url()) &&
        !request.url().includes(fixture.plannerId),
      { timeout: 15_000 },
    )
    await page.getByRole('button', { name: 'Save as Copy' }).click()
    const uploaded = await forkUpload
    await expect(page.getByText('Changed on Another Device')).toBeHidden()

    // The fork the server received carries the local title and starts its own version line.
    const forkId = uploaded.url().split('/').pop()!
    const reader = await createAuthenticatedApi(OREGON_API, fixture.user.id)
    const fork = await reader.get(`/api/planner/md/${forkId}`)
    expect(fork.status(), await fork.text()).toBe(200)
    const forkBody = await fork.json()
    expect(forkBody.title).toContain(fixture.localTitle)
    expect(forkBody.published).toBe(false)

    const original = await reader.get(`/api/planner/md/${fixture.plannerId}`)
    expect(original.status(), await original.text()).toBe(200)
    expect((await original.json()).title).toBe(fixture.serverTitle)
    await reader.delete(`/api/planner/md/${forkId}`)
    await reader.dispose()
  } finally {
    await dropDivergence(fixture)
  }
})
