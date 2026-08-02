import { test, expect } from '@playwright/test'
import type { APIRequestContext } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { authHeaders, createAuthenticatedApi } from '../src/auth'
import { becomesTrue, holdsThroughout } from '../src/consistency'
import { cookieHeader } from '../src/cookies'
import {
  RYW_COOKIE,
  capturedGtid,
  parseGtidSet,
  reachesFirstTransaction,
  rywCookieValue,
  transactionCount,
} from '../src/gtid'
import { gtidFallbackRatio, metricsSourceConfigured } from '../src/metrics'
import { PLANNER_KEYWORD, plannerPayload } from '../src/plannerContent'
import { closeSeedPool, createUser, deleteUser, type SeededUser } from '../src/seed'
import {
  MAX_GTID_FALLBACK_RATIO,
  OREGON_API,
  OWN_GTID_TRANSACTION_CAP,
  SEOUL_API,
  SAFETY_WINDOW_MS,
} from '../src/staging'
import { edgeContext } from '../src/edge'

// The read-your-writes gate, exercised across the two regions it exists for: a write served by
// Seoul commits on the Oregon primary and mints ryw_gtid, and a read carrying that cookie must see
// the write at every instant of the replication window — never a bare 404 that resolves later.
//
// Writes are driven through Seoul because the gate is registered only where routing is enabled
// (docs/multi-region-request-paths.md §3); an Oregon write mints no cookie because Oregon reads
// the pool it wrote to.

test.describe.configure({ mode: 'default' })

// Safety-window tests hold their probe loop open for SAFETY_WINDOW_MS per region, so the test
// budget must scale with the window or a widened window times the suite out.
test.describe.configure({ timeout: SAFETY_WINDOW_MS * 4 + 60_000 })

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

function reader(host: string, cookie: string): Promise<APIRequestContext> {
  return edgeContext({ baseURL: host, extraHTTPHeaders: { Cookie: cookie } })
}

async function listedIds(context: APIRequestContext, query: string): Promise<string[]> {
  const response = await context.get(`/api/planner/md/published?${query}`)
  expect(response.status(), await response.text()).toBe(200)
  return ((await response.json()).content as Array<{ id: string }>).map((planner) => planner.id)
}

test('a write served by Seoul is readable in both regions at every instant, with the cookie', async () => {
  const fixture = await createFixture(SEOUL_API, 'ryw-read')
  const readers: Array<[string, APIRequestContext]> = []

  try {
    const created = await fixture.api.put(`/api/planner/md/${fixture.plannerId}`, {
      data: plannerPayload(fixture.plannerId, fixture.title),
    })
    expect([200, 201], await created.text()).toContain(created.status())

    const minted = rywCookieValue(created)
    expect(minted, `the write on ${SEOUL_API} minted no ${RYW_COOKIE}`).not.toBeNull()

    const cookie = cookieHeader(authHeaders(fixture.user.id).Cookie, `${RYW_COOKIE}=${minted}`)
    for (const host of [OREGON_API, SEOUL_API]) {
      readers.push([host, await reader(host, cookie)])
    }

    const probes = await holdsThroughout(async (attempt) => {
      for (const [host, context] of readers) {
        const read = await context.get(`/api/planner/md/${fixture.plannerId}`)
        expect(read.status(), `${host}, probe ${attempt}: ${await read.text()}`).toBe(200)
      }
    })
    expect(probes).toBeGreaterThan(1)
  } finally {
    for (const [, context] of readers) await context.dispose()
    await dropFixture(fixture)
  }
})

test('without the cookie the same listing converges only after the replication lag', async () => {
  const fixture = await createFixture(SEOUL_API, 'ryw-lag')
  let anonymous: APIRequestContext | undefined

  try {
    const created = await fixture.api.put(`/api/planner/md/${fixture.plannerId}`, {
      data: plannerPayload(fixture.plannerId, fixture.title),
    })
    expect([200, 201], await created.text()).toContain(created.status())

    const published = await fixture.api.put(`/api/planner/md/${fixture.plannerId}/publish`, {
      data: { published: true },
    })
    expect(published.status(), await published.text()).toBe(200)

    // No cookie, and a listing rather than a by-id read: the by-id path would be answered by the
    // primary re-check (§5) and show no lag at all.
    anonymous = await edgeContext({ baseURL: SEOUL_API })
    const lagMs = await becomesTrue(
      async () => (await listedIds(anonymous!, 'page=0&size=50')).includes(fixture.plannerId),
      { what: `${fixture.plannerId} on the ungated ${SEOUL_API} listing` },
    )

    test.info().annotations.push({
      type: 'replication-lag',
      description: `${lagMs}ms for an ungated listing on ${SEOUL_API}`,
    })
  } finally {
    await anonymous?.dispose()
    await dropFixture(fixture)
  }
})

test('the cookie carries the transaction own GTID rather than the primary executed set', async () => {
  const fixture = await createFixture(SEOUL_API, 'ryw-shape')

  try {
    const created = await fixture.api.put(`/api/planner/md/${fixture.plannerId}`, {
      data: plannerPayload(fixture.plannerId, fixture.title),
    })
    expect([200, 201], await created.text()).toContain(created.status())

    const gtid = capturedGtid(created)
    expect(gtid, `the write on ${SEOUL_API} minted no ${RYW_COOKIE}`).not.toBeNull()

    const sources = parseGtidSet(gtid!)
    expect(transactionCount(sources), `wide capture: ${gtid}`).toBeLessThanOrEqual(
      OWN_GTID_TRANSACTION_CAP,
    )
    expect(reachesFirstTransaction(sources), `executed-set capture: ${gtid}`).toBe(false)
  } finally {
    await dropFixture(fixture)
  }
})

test('publishing an already-published planner mints no ryw_gtid', async () => {
  const fixture = await createFixture(SEOUL_API, 'ryw-idem')

  try {
    const created = await fixture.api.put(`/api/planner/md/${fixture.plannerId}`, {
      data: plannerPayload(fixture.plannerId, fixture.title),
    })
    expect([200, 201], await created.text()).toContain(created.status())

    const published = await fixture.api.put(`/api/planner/md/${fixture.plannerId}/publish`, {
      data: { published: true },
    })
    expect(published.status(), await published.text()).toBe(200)
    expect(rywCookieValue(published), 'the state change minted no cookie').not.toBeNull()

    const republished = await fixture.api.put(`/api/planner/md/${fixture.plannerId}/publish`, {
      data: { published: true },
    })
    expect(republished.status(), await republished.text()).toBe(200)
    expect(rywCookieValue(republished), 'a no-op publish committed something').toBeNull()
  } finally {
    await dropFixture(fixture)
  }
})

test('the cookie covers the filter-index rebuild as well as the publish itself', async () => {
  const fixture = await createFixture(SEOUL_API, 'ryw-union')
  let gated: APIRequestContext | undefined

  try {
    const created = await fixture.api.put(`/api/planner/md/${fixture.plannerId}`, {
      data: plannerPayload(fixture.plannerId, fixture.title),
    })
    expect([200, 201], await created.text()).toContain(created.status())

    const published = await fixture.api.put(`/api/planner/md/${fixture.plannerId}/publish`, {
      data: { published: true },
    })
    expect(published.status(), await published.text()).toBe(200)

    const minted = rywCookieValue(published)
    expect(minted, `the publish on ${SEOUL_API} minted no ${RYW_COOKIE}`).not.toBeNull()

    // The keyword facet is served by planner_keyword_filter, which the publish rebuilds in a second
    // transaction after commit. A cookie covering only the first commit gates past that rebuild.
    gated = await reader(SEOUL_API, `${RYW_COOKIE}=${minted}`)
    await holdsThroughout(async (attempt) => {
      const ids = await listedIds(gated!, `page=0&size=50&keyword=${PLANNER_KEYWORD}`)
      expect(ids, `probe ${attempt}`).toContain(fixture.plannerId)
    })
  } finally {
    await gated?.dispose()
    await dropFixture(fixture)
  }
})

test('the GTID capture takes the tracker path rather than the fallback', async () => {
  test.skip(!metricsSourceConfigured(), 'E2E_METRICS_SOURCE is unset')

  const ratio = await gtidFallbackRatio()
  expect(ratio, 'the scrape carries no gtid.capture samples').not.toBeNull()
  expect(ratio!).toBeLessThanOrEqual(MAX_GTID_FALLBACK_RATIO)
})
