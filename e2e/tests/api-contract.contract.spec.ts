import { test, expect } from '@playwright/test'
import {
  PaginatedPlannersSchema,
  PublishedPlannerDetailSchema,
} from '@/pages/planner/schemas/PlannerListSchemas'

// These parse live responses with the schemas the browser itself uses, so a server field that
// changes type or disappears fails here rather than in a user's console. Backend tests pin the
// server's own shape; nothing else compares the two declarations.

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

// An envelope parses on an empty database without ever exercising the item schema, so the item
// assertion is only meaningful once something is published. planner-journey.spec.ts publishes one;
// this fails rather than skips when the list is empty, because a check that quietly passes on no
// data is the one that hides a broken contract.
test('a published planner item matches the client schema', async ({ request }) => {
  const listResponse = await request.get('/api/planner/md/published?page=0&size=1')
  const list = PaginatedPlannersSchema.parse(await listResponse.json())
  expect(list.content.length).toBeGreaterThan(0)

  const id = list.content[0]!.id
  const detailResponse = await request.get(`/api/planner/md/published/${id}`)
  expect(detailResponse.status()).toBe(200)

  const parsed = PublishedPlannerDetailSchema.safeParse(await detailResponse.json())
  expect(parsed.error?.issues ?? []).toEqual([])
  expect(parsed.success).toBe(true)
})

test('an unauthenticated identity probe returns an empty body rather than a rejection', async ({
  request,
}) => {
  const response = await request.get('/api/auth/me')
  expect(response.status()).toBe(200)
  expect((await response.text()).trim()).toBe('')
})
