import { test, expect } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import {
  PaginatedPlannersSchema,
  PublishedPlannerDetailSchema,
} from '@/pages/planner/schemas/PlannerListSchemas'
import { ServerPlannerSummaryPageSchema } from '@/pages/planner/schemas/PlannerSchemas'
import { createAuthenticatedApi } from '../src/auth'
import { becomesTrue } from '../src/consistency'
import { closeSeedPool, createUser, deleteUser } from '../src/seed'
import { plannerPayload } from '../src/plannerContent'

// These parse live responses with the schemas the browser itself uses, so a server field that
// changes type or disappears fails here rather than in a user's console. Backend tests pin the
// server's own shape; nothing else compares the two declarations.

test.afterAll(closeSeedPool)

test('the published list envelope matches the client schema', async ({ request }) => {
  const response = await request.get('/api/planner/md/published?page=0&size=20')
  expect(response.status()).toBe(200)

  const parsed = PaginatedPlannersSchema.safeParse(await response.json())
  expect(parsed.error?.issues ?? []).toEqual([])
  expect(parsed.success).toBe(true)
})

test('the recommended list envelope matches the client schema', async ({ request }) => {
  const response = await request.get('/api/planner/md/recommended?page=0&size=20')
  expect(response.status()).toBe(200)

  const parsed = PaginatedPlannersSchema.safeParse(await response.json())
  expect(parsed.error?.issues ?? []).toEqual([])
  expect(parsed.success).toBe(true)
})

// An envelope parses on an empty database without ever exercising the item schema, so this test
// publishes its own planner rather than depending on one existing — the suites run against fresh
// databases where ambient data is never guaranteed, and the journey suite deletes what it makes.
test('a published planner item matches the client schema', async ({ request, baseURL }) => {
  const user = await createUser('contract')
  const api = await createAuthenticatedApi(baseURL!, user.id)
  const plannerId = randomUUID()

  try {
    const created = await api.put(`/api/planner/md/${plannerId}`, {
      data: plannerPayload(plannerId, `e2e contract ${plannerId.slice(0, 8)}`),
    })
    expect([200, 201], await created.text()).toContain(created.status())
    const published = await api.put(`/api/planner/md/${plannerId}/publish`, {
      data: { published: true },
    })
    expect(published.status(), await published.text()).toBe(200)

    const detailResponse = await request.get(`/api/planner/md/published/${plannerId}`)
    expect(detailResponse.status()).toBe(200)

    const parsed = PublishedPlannerDetailSchema.safeParse(await detailResponse.json())
    expect(parsed.error?.issues ?? []).toEqual([])
    expect(parsed.success).toBe(true)
  } finally {
    await api.delete(`/api/planner/md/${plannerId}`)
    await api.dispose()
    await deleteUser(user)
  }
})

test('an unauthenticated identity probe returns an empty body rather than a rejection', async ({
  request,
}) => {
  const response = await request.get('/api/auth/me')
  expect(response.status()).toBe(204)
  expect((await response.text()).trim()).toBe('')
})

// The tombstone visibility contract (docs/adr/088): the default own-list serves live rows only
// and never carries a deletedAt key — the wire an already-deployed strict client parses — while
// the includeDeleted flavor adds tombstoned rows under the same client schema. Raw-body key
// checks, because an optional schema field cannot notice an unexpected presence.
test('the own list hides tombstones and their key until a sync pull asks', async ({ baseURL }) => {
  const user = await createUser('contract-tombstone')
  const api = await createAuthenticatedApi(baseURL!, user.id)
  const liveId = randomUUID()
  const deletedId = randomUUID()

  interface RawSummary {
    id: string
    deletedAt?: string
  }
  const ownList = async (flavor: string): Promise<RawSummary[]> => {
    const response = await api.get(`/api/planner/md?page=0&size=100${flavor}`)
    expect(response.status(), await response.text()).toBe(200)
    const body = (await response.json()) as { content: RawSummary[] }
    const parsed = ServerPlannerSummaryPageSchema.safeParse(body)
    expect(parsed.error?.issues ?? []).toEqual([])
    return body.content
  }

  try {
    for (const id of [liveId, deletedId]) {
      const created = await api.put(`/api/planner/md/${id}`, {
        data: plannerPayload(id, `e2e contract-tombstone ${id.slice(0, 8)}`),
      })
      expect([200, 201], await created.text()).toContain(created.status())
    }
    const deleted = await api.delete(`/api/planner/md/${deletedId}`)
    expect(deleted.status(), await deleted.text()).toBe(204)

    // The list is an ungated replica read, so both flavors are polled to convergence before
    // the shape assertions run on the settled bodies.
    let plain: RawSummary[] = []
    await becomesTrue(
      async () => {
        plain = await ownList('')
        return (
          plain.some((row) => row.id === liveId) && !plain.some((row) => row.id === deletedId)
        )
      },
      { what: `${liveId} live and ${deletedId} gone on the default own list` },
    )
    let withDeleted: RawSummary[] = []
    await becomesTrue(
      async () => {
        withDeleted = await ownList('&includeDeleted=true')
        // Presence alone is not convergence: before the deletion replicates the row rides
        // the list as still-live, so the condition is the tombstone itself.
        return withDeleted.some((row) => row.id === deletedId && row.deletedAt !== undefined)
      },
      { what: `${deletedId}'s tombstone on the includeDeleted list` },
    )

    const liveRow = plain.find((row) => row.id === liveId)!
    expect('deletedAt' in liveRow, 'a live row carried the deletedAt key').toBe(false)

    const tombstone = withDeleted.find((row) => row.id === deletedId)!
    expect(typeof tombstone.deletedAt).toBe('string')
    const liveWithFlavor = withDeleted.find((row) => row.id === liveId)!
    expect('deletedAt' in liveWithFlavor, 'a live row grew the key under includeDeleted').toBe(
      false,
    )
  } finally {
    await api.delete(`/api/planner/md/${liveId}`)
    await api.dispose()
    await deleteUser(user)
  }
})
