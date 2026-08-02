import { test, expect } from '@playwright/test'
import type { APIRequestContext } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { authHeaders, createAuthenticatedApi } from '../src/auth'
import { holdsThroughout } from '../src/consistency'
import { plannerPayload } from '../src/plannerContent'
import { closeSeedPool, createUser, deleteUser, type SeededUser } from '../src/seed'
import { OREGON_API, SEOUL_API } from '../src/staging'
import { edgeContext } from '../src/edge'

// The consistency path for a client that carries no cookie: a by-id dereference that misses on the
// stale replica is re-checked against the primary through the bulkhead, and a delete is masked by
// the tombstone so a stale replica hit cannot resurrect the row
// (docs/multi-region-request-paths.md §5).
//
// Writes go to Oregon here precisely so no ryw_gtid exists: Oregon registers no gate, so the reader
// against Seoul has nothing to present and the re-check is the only thing that can answer.

test.describe.configure({ mode: 'default' })

test.afterAll(closeSeedPool)

interface Fixture {
  user: SeededUser
  api: APIRequestContext
  plannerId: string
  title: string
}

async function createFixture(label: string): Promise<Fixture> {
  const user = await createUser(label)
  const api = await createAuthenticatedApi(OREGON_API, user.id)
  const plannerId = randomUUID()
  return { user, api, plannerId, title: `e2e ${label} ${plannerId.slice(0, 8)}` }
}

async function writePlanner(fixture: Fixture): Promise<void> {
  const created = await fixture.api.put(`/api/planner/md/${fixture.plannerId}`, {
    data: plannerPayload(fixture.plannerId, fixture.title),
  })
  expect([200, 201], await created.text()).toContain(created.status())
}

async function publishPlanner(fixture: Fixture): Promise<void> {
  const published = await fixture.api.put(`/api/planner/md/${fixture.plannerId}/publish`, {
    data: { published: true },
  })
  expect(published.status(), await published.text()).toBe(200)
}

async function dropFixture(fixture: Fixture): Promise<void> {
  await fixture.api.delete(`/api/planner/md/${fixture.plannerId}`)
  await fixture.api.dispose()
  await deleteUser(fixture.user)
}

test('a by-id read of a just-created planner is answered by the primary re-check', async () => {
  const fixture = await createFixture('recheck-owner')
  let owner: APIRequestContext | undefined

  try {
    await writePlanner(fixture)

    owner = await edgeContext({
      baseURL: SEOUL_API,
      extraHTTPHeaders: authHeaders(fixture.user.id),
    })
    const probes = await holdsThroughout(async (attempt) => {
      const read = await owner!.get(`/api/planner/md/${fixture.plannerId}`)
      expect(read.status(), `probe ${attempt}: ${await read.text()}`).toBe(200)
    })
    expect(probes).toBeGreaterThan(1)
  } finally {
    await owner?.dispose()
    await dropFixture(fixture)
  }
})

test('a just-published planner is readable by id anonymously from the replica region', async () => {
  const fixture = await createFixture('recheck-public')
  let anonymous: APIRequestContext | undefined

  try {
    await writePlanner(fixture)
    await publishPlanner(fixture)

    anonymous = await edgeContext({ baseURL: SEOUL_API })
    await holdsThroughout(async (attempt) => {
      const read = await anonymous!.get(`/api/planner/md/published/${fixture.plannerId}`)
      expect(read.status(), `probe ${attempt}: ${await read.text()}`).toBe(200)
    })
  } finally {
    await anonymous?.dispose()
    await dropFixture(fixture)
  }
})

test(
  'a deleted planner is absent from every read in every region at every instant',
  { tag: '@destructive' },
  async () => {
    const fixture = await createFixture('recheck-deleted')
    const owners: Array<[string, APIRequestContext]> = []
    const visitors: Array<[string, APIRequestContext]> = []

    try {
      await writePlanner(fixture)
      await publishPlanner(fixture)

      const deleted = await fixture.api.delete(`/api/planner/md/${fixture.plannerId}`)
      expect(deleted.status(), await deleted.text()).toBe(204)

      for (const host of [SEOUL_API, OREGON_API]) {
        owners.push([
          host,
          await edgeContext({
            baseURL: host,
            extraHTTPHeaders: authHeaders(fixture.user.id),
          }),
        ])
        visitors.push([host, await edgeContext({ baseURL: host })])
      }

      await holdsThroughout(async (attempt) => {
        for (const [host, context] of owners) {
          const owned = await context.get(`/api/planner/md/${fixture.plannerId}`)
          expect(owned.status(), `${host} owner by-id, probe ${attempt}`).toBe(404)
        }
        for (const [host, context] of visitors) {
          const published = await context.get(`/api/planner/md/published/${fixture.plannerId}`)
          expect(published.status(), `${host} published by-id, probe ${attempt}`).toBe(404)
        }
      })
    } finally {
      for (const [, context] of [...owners, ...visitors]) await context.dispose()
      await dropFixture(fixture)
    }
  },
)
