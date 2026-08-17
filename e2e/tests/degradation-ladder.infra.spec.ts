import { test, expect } from '@playwright/test'
import type { APIRequestContext } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { authHeaders, createAuthenticatedApi } from '../src/auth'
import { holdsThroughout } from '../src/consistency'
import {
  BLACKLIST_SKIP_METRIC,
  CHAOS_MODES,
  DEGRADATION_CODES,
  chaosActive,
  chaosMode,
  type ChaosMode,
  type DegradationBody,
} from '../src/degradation'
import { counterTotal, metricsSourceConfigured, scrapeMetrics } from '../src/metrics'
import { plannerPayload } from '../src/plannerContent'
import { closeSeedPool, createUser, deleteUser, type SeededUser } from '../src/seed'
import { SEOUL_API } from '../src/staging'
import { edgeContext } from '../src/edge'

// One scenario per row of the degradation ladder (docs/multi-region-request-paths.md §10). The
// failures themselves are injected out of band over SSM; a scenario runs only while its injection
// is the active one, so the whole file is inert on an undisturbed environment.

test.describe.configure({ mode: 'default' })

// The first request after a dependency is severed pays a connection-death discovery cost beyond
// the steady-state command timeout (Lettuce must observe the broken socket before the fail-open
// or typed-503 path fires); the property is the degraded RESPONSE, not its latency, so the budget
// is generous.
test.describe.configure({ timeout: 90_000 })

test.afterAll(closeSeedPool)

function requiresChaos(mode: ChaosMode): void {
  test.skip(!chaosActive(mode), `E2E_CHAOS is ${chaosMode() ?? 'unset'}, not ${mode}`)
}

interface Fixture {
  user: SeededUser
  api: APIRequestContext
  plannerId: string
  title: string
}

async function createFixture(label: string): Promise<Fixture> {
  const user = await createUser(label)
  const api = await createAuthenticatedApi(SEOUL_API, user.id)
  const plannerId = randomUUID()
  return { user, api, plannerId, title: `e2e ${label} ${plannerId.slice(0, 8)}` }
}

async function dropFixture(fixture: Fixture): Promise<void> {
  await fixture.api.delete(`/api/planner/md/${fixture.plannerId}`)
  await fixture.api.dispose()
  await deleteUser(fixture.user)
}

function writePlanner(fixture: Fixture) {
  return fixture.api.put(`/api/planner/md/${fixture.plannerId}`, {
    data: plannerPayload(fixture.plannerId, fixture.title),
  })
}

test('a blackholed primary turns writes into a typed 503 while reads keep serving', async () => {
  requiresChaos(CHAOS_MODES.primaryBlackholed)

  const fixture = await createFixture('chaos-primary')
  let anonymous: APIRequestContext | undefined

  try {
    const write = await writePlanner(fixture)
    expect(write.status(), await write.text()).toBe(503)
    expect([DEGRADATION_CODES.write, DEGRADATION_CODES.edgeRewritten]).toContain(
      ((await write.json()) as DegradationBody).code,
    )

    anonymous = await edgeContext({ baseURL: SEOUL_API })
    await holdsThroughout(async (attempt) => {
      const list = await anonymous!.get('/api/planner/md/published?page=0&size=20')
      expect(list.status(), `replica read, probe ${attempt}`).toBe(200)
    })
  } finally {
    await anonymous?.dispose()
    await dropFixture(fixture)
  }
})

test('an exhausted request pool answers rather than pinning the thread', async () => {
  requiresChaos(CHAOS_MODES.poolExhausted)

  const fixture = await createFixture('chaos-pool')

  try {
    const write = await writePlanner(fixture)
    expect(write.status(), await write.text()).toBe(503)
    expect(((await write.json()) as DegradationBody).code).toBe(DEGRADATION_CODES.write)
  } finally {
    await dropFixture(fixture)
  }
})

test('a saturated bulkhead queues re-checks without touching the write path', async () => {
  requiresChaos(CHAOS_MODES.bulkheadSaturated)

  const fixture = await createFixture('chaos-bulkhead')
  let owner: APIRequestContext | undefined

  try {
    const write = await writePlanner(fixture)
    expect([200, 201], await write.text()).toContain(write.status())

    owner = await edgeContext({
      baseURL: SEOUL_API,
      extraHTTPHeaders: authHeaders(fixture.user.id),
    })
    await holdsThroughout(async (attempt) => {
      const read = await owner!.get(`/api/planner/md/${fixture.plannerId}`)
      expect([200, 404], `by-id read, probe ${attempt}`).toContain(read.status())
    })
  } finally {
    await owner?.dispose()
    await dropFixture(fixture)
  }
})

test('an unreachable auth Redis fails the blacklist check open', async () => {
  requiresChaos(CHAOS_MODES.authRedisDown)

  const user = await createUser('chaos-auth-redis')
  let owner: APIRequestContext | undefined

  try {
    owner = await edgeContext({
      baseURL: SEOUL_API,
      extraHTTPHeaders: authHeaders(user.id),
    })
    const identity = await owner.get('/api/auth/me')
    expect(identity.status(), await identity.text()).toBe(200)

    if (metricsSourceConfigured()) {
      const skipped = counterTotal(await scrapeMetrics(), BLACKLIST_SKIP_METRIC, {})
      expect(skipped, 'the skipped-check counter did not move').toBeGreaterThan(0)
    }
  } finally {
    await owner?.dispose()
    await deleteUser(user)
  }
})

test('an unreachable rate-limit Redis refuses the write with its own typed 503', async () => {
  requiresChaos(CHAOS_MODES.rateLimitRedisDown)

  const fixture = await createFixture('chaos-ratelimit')

  try {
    // Which code reaches the client depends on what sits in front of the app: the compose
    // stack's nginx runs proxy_intercept_errors and rewrites every backend 5xx to
    // BACKEND_UNAVAILABLE, while the k3s fleet (cloudflared -> Traefik) passes the typed body
    // through. The 503 is the invariant; the body is the environment's business.
    const write = await writePlanner(fixture)
    expect(write.status(), await write.text()).toBe(503)
    expect([DEGRADATION_CODES.rateLimit, DEGRADATION_CODES.edgeRewritten]).toContain(
      ((await write.json()) as DegradationBody).code,
    )
  } finally {
    await dropFixture(fixture)
  }
})

test('a running migration is left unbounded rather than aborted mid-change', async () => {
  requiresChaos(CHAOS_MODES.flywayMigrating)

  const anonymous = await edgeContext({ baseURL: SEOUL_API })

  try {
    await holdsThroughout(async (attempt) => {
      const list = await anonymous.get('/api/planner/md/published?page=0&size=20')
      expect(list.status(), `read during migration, probe ${attempt}`).toBe(200)
    })
  } finally {
    await anonymous.dispose()
  }
})
