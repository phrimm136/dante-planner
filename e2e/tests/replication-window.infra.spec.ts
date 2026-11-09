import { test, expect } from '@playwright/test'
import type { APIRequestContext } from '@playwright/test'
import { execFileSync } from 'node:child_process'
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

// The freshness markers, proven rather than presumed: natural local lag is a few milliseconds, so
// marked-read-freshness.spec.ts can go green without a marker ever making a read skip the replica.
// Freezing the replica's SQL applier holds the replication window open across the whole assertion;
// every probe asks the replica itself that it still lacks the row, so each Seoul answer that
// carries the planner was served by the authority after the written scope's marker skipped the
// replica.
//
// Writes go to Oregon so no ryw_gtid exists (docs/multi-region-request-paths.md §3): the reader
// against Seoul has nothing to present, and the markers are the only thing that can answer.

test.describe.configure({ mode: 'default' })
test.describe.configure({ timeout: SAFETY_WINDOW_MS * 4 + 60_000 })

test.afterAll(closeSeedPool)

function replicaHoldsPlanner(plannerId: string): boolean {
  const count = execFileSync(
    'docker',
    [
      'exec',
      REPLICA_CONTAINER,
      'sh',
      '-c',
      'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B "$MYSQL_DATABASE" -e "$0"',
      `SELECT COUNT(*) FROM planner_content WHERE planner_id = UUID_TO_BIN('${plannerId}')`,
    ],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] },
  ).trim()
  return count !== '0'
}

test('marked scopes are answered from the primary while replication is frozen', async () => {
  test.skip(
    !replicaReachable(),
    `${REPLICA_CONTAINER} is not reachable over docker; only the local rig can freeze replication`,
  )

  const user = await createUser('frozen-marker')
  const api = await createAuthenticatedApi(OREGON_API, user.id)
  const plannerId = randomUUID()
  const title = `e2e frozen-marker ${plannerId.slice(0, 8)}`

  let owner: APIRequestContext | undefined
  let anonymous: APIRequestContext | undefined

  try {
    freezeReplication()
    try {
      const created = await api.put(`/api/planner/md/${plannerId}`, {
        data: plannerPayload(plannerId, title),
      })
      expect([200, 201], await created.text()).toContain(created.status())

      const published = await api.post(`/api/planner/md/${plannerId}/publish`)
      expect(published.status(), await published.text()).toBe(200)

      owner = await edgeContext({ baseURL: SEOUL_API, extraHTTPHeaders: authHeaders(user.id) })
      anonymous = await edgeContext({ baseURL: SEOUL_API })

      const probes = await holdsThroughout(async (attempt) => {
        expect(
          replicaHoldsPlanner(plannerId),
          `frozen replica applied the create, probe ${attempt}`,
        ).toBe(false)

        const owned = await owner!.get(`/api/planner/md/${plannerId}`)
        expect(owned.status(), `owner by-id, probe ${attempt}: ${await owned.text()}`).toBe(200)

        const published = await anonymous!.get(`/api/planner/md/published/${plannerId}`)
        expect(
          published.status(),
          `anonymous published by-id, probe ${attempt}: ${await published.text()}`,
        ).toBe(200)

        const list = await anonymous!.get('/api/planner/md/published?page=0&size=50')
        expect(list.status(), `listing, probe ${attempt}`).toBe(200)
        const ids = ((await list.json()).content as Array<{ id: string }>).map((p) => p.id)
        expect(ids, `listing missed the published row, probe ${attempt}`).toContain(plannerId)
      })
      expect(probes).toBeGreaterThan(1)
    } finally {
      releaseReplication()
    }

    // The window is released above, so convergence doubles as proof the rig is healthy again.
    await becomesTrue(async () => replicaHoldsPlanner(plannerId), {
      what: `${plannerId} on ${REPLICA_CONTAINER} after the window released`,
    })
  } finally {
    await owner?.dispose()
    await anonymous?.dispose()
    await api.delete(`/api/planner/md/${plannerId}`)
    await api.dispose()
    await deleteUser(user)
  }
})
