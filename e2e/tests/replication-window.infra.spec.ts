import { test, expect } from '@playwright/test'
import type { APIRequestContext } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { authHeaders, createAuthenticatedApi } from '../src/auth'
import { becomesTrue, holdsThroughout } from '../src/consistency'
import { plannerPayload } from '../src/plannerContent'
import {
  freezeReplication,
  releaseReplication,
  replicaReachable,
  REPLICA_CONTAINER,
} from '../src/replication'
import { closeSeedPool, createUser, deleteUser } from '../src/seed'
import { OREGON_API, SAFETY_WINDOW_MS, SEOUL_API } from '../src/staging'
import { edgeContext } from '../src/edge'

// The primary re-check, proven rather than presumed: natural local lag is a few milliseconds, so
// replica-miss-recheck.spec.ts can go green without the re-check ever firing. Freezing the
// replica's SQL applier holds the replication window open across the whole assertion, so a
// replica answer is impossible and every 200 out of Seoul is the primary's.
//
// Writes go to Oregon so no ryw_gtid exists (docs/multi-region-request-paths.md §3): the reader
// against Seoul has nothing to present, and the re-check is the only thing that can answer.

test.describe.configure({ mode: 'default' })
test.describe.configure({ timeout: SAFETY_WINDOW_MS * 4 + 60_000 })

test.afterAll(closeSeedPool)

test('the by-id re-check answers from the primary while replication is frozen', async () => {
  test.skip(
    !replicaReachable(),
    `${REPLICA_CONTAINER} is not reachable over docker; only the local rig can freeze replication`,
  )

  const user = await createUser('frozen-recheck')
  const api = await createAuthenticatedApi(OREGON_API, user.id)
  const plannerId = randomUUID()
  const title = `e2e frozen-recheck ${plannerId.slice(0, 8)}`

  let owner: APIRequestContext | undefined
  let anonymous: APIRequestContext | undefined

  try {
    freezeReplication()
    try {
      const created = await api.put(`/api/planner/md/${plannerId}`, {
        data: plannerPayload(plannerId, title),
      })
      expect([200, 201], await created.text()).toContain(created.status())

      const published = await api.put(`/api/planner/md/${plannerId}/publish`, {
        data: { published: true },
      })
      expect(published.status(), await published.text()).toBe(200)

      owner = await edgeContext({ baseURL: SEOUL_API, extraHTTPHeaders: authHeaders(user.id) })
      anonymous = await edgeContext({ baseURL: SEOUL_API })

      const probes = await holdsThroughout(async (attempt) => {
        const owned = await owner!.get(`/api/planner/md/${plannerId}`)
        expect(owned.status(), `owner by-id, probe ${attempt}: ${await owned.text()}`).toBe(200)

        const published = await anonymous!.get(`/api/planner/md/published/${plannerId}`)
        expect(
          published.status(),
          `anonymous published by-id, probe ${attempt}: ${await published.text()}`,
        ).toBe(200)

        // The listing has no re-check (docs/multi-region-request-paths.md §5): its miss is what
        // proves the window is really frozen and the 200s above are the primary's.
        const list = await anonymous!.get('/api/planner/md/published?page=0&size=50')
        expect(list.status(), `listing, probe ${attempt}`).toBe(200)
        const ids = ((await list.json()).content as Array<{ id: string }>).map((p) => p.id)
        expect(ids, `frozen listing served the row, probe ${attempt}`).not.toContain(plannerId)
      })
      expect(probes).toBeGreaterThan(1)
    } finally {
      releaseReplication()
    }

    // The window is released above, so convergence doubles as proof the rig is healthy again.
    await becomesTrue(
      async () => {
        const list = await anonymous!.get('/api/planner/md/published?page=0&size=50')
        expect(list.status()).toBe(200)
        const ids = ((await list.json()).content as Array<{ id: string }>).map((p) => p.id)
        return ids.includes(plannerId)
      },
      { what: `${plannerId} on the ${SEOUL_API} listing after the window released` },
    )
  } finally {
    await owner?.dispose()
    await anonymous?.dispose()
    await api.delete(`/api/planner/md/${plannerId}`)
    await api.dispose()
    await deleteUser(user)
  }
})
