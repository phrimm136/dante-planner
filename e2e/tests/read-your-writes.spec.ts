import { test, expect } from '@playwright/test'
import type { APIRequestContext } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { authHeaders, createAuthenticatedApi } from '../src/auth'
import { gtidFallbackRatio, metricsSourceConfigured } from '../src/metrics'
import { plannerPayload } from '../src/plannerContent'
import { closeSeedPool, createUser, deleteUser, type SeededUser } from '../src/seed'
import { MAX_GTID_FALLBACK_RATIO, OREGON_API, SEOUL_API } from '../src/staging'
import { edgeContext } from '../src/edge'

test.describe.configure({ mode: 'default' })

test.afterAll(closeSeedPool)

interface Fixture {
  user: SeededUser
  api: APIRequestContext
  plannerId: string
  title: string
}

async function createFixture(host: string, label: string): Promise<Fixture> {
  const user = await createUser(label)
  const api = await createAuthenticatedApi(host, user.id)
  const plannerId = randomUUID()
  return { user, api, plannerId, title: `e2e ${label} ${plannerId.slice(0, 8)}` }
}

async function dropFixture(fixture: Fixture): Promise<void> {
  await fixture.api.delete(`/api/planner/md/${fixture.plannerId}`)
  await fixture.api.dispose()
  await deleteUser(fixture.user)
}

async function listedIds(context: APIRequestContext, query: string): Promise<string[]> {
  const response = await context.get(`/api/planner/md/published?${query}`)
  expect(response.status(), await response.text()).toBe(200)
  return ((await response.json()).content as Array<{ id: string }>).map((planner) => planner.id)
}

test('the listing serves a just-published planner through its catalog marker', async () => {
  const fixture = await createFixture(SEOUL_API, 'ryw-uncookied')
  let anonymous: APIRequestContext | undefined

  try {
    const created = await fixture.api.put(`/api/planner/md/${fixture.plannerId}`, {
      data: plannerPayload(fixture.plannerId, fixture.title),
    })
    expect([200, 201], await created.text()).toContain(created.status())

    const published = await fixture.api.post(`/api/planner/md/${fixture.plannerId}/publish`)
    expect(published.status(), await published.text()).toBe(200)

    // The publish raised the public catalog's marker, so a replica that has not
    // applied it is skipped and the first listing after the response already carries the row.
    anonymous = await edgeContext({ baseURL: SEOUL_API })
    expect(await listedIds(anonymous, 'page=0&size=50')).toContain(fixture.plannerId)
  } finally {
    await anonymous?.dispose()
    await dropFixture(fixture)
  }
})

test('a settings save in one region is read in the other at once', async () => {
  const user = await createUser('ryw-settings')
  const writer = await createAuthenticatedApi(OREGON_API, user.id)
  let reader: APIRequestContext | undefined

  try {
    reader = await edgeContext({ baseURL: SEOUL_API, extraHTTPHeaders: authHeaders(user.id) })

    for (const syncEnabled of [true, false, true]) {
      const saved = await writer.put('/api/user/settings', { data: { syncEnabled } })
      expect(saved.status(), await saved.text()).toBe(200)

      const read = await reader.get('/api/user/settings')
      expect(read.status(), await read.text()).toBe(200)
      expect((await read.json()).syncEnabled).toBe(syncEnabled)
    }
  } finally {
    await reader?.dispose()
    await writer.dispose()
    await deleteUser(user)
  }
})

test('the GTID capture takes the tracker path rather than the fallback', async () => {
  test.skip(!metricsSourceConfigured(), 'E2E_METRICS_SOURCE is unset')

  const ratio = await gtidFallbackRatio()
  expect(ratio, 'the scrape carries no gtid.capture samples').not.toBeNull()
  expect(ratio!).toBeLessThanOrEqual(MAX_GTID_FALLBACK_RATIO)
})
