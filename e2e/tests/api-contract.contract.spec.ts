import { test, expect } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import {
  PaginatedPlannersSchema,
  PublishedPlannerDetailSchema,
} from '@/pages/planner/schemas/PlannerListSchemas'
import { createAuthenticatedApi } from '../src/auth'
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
