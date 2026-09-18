import { test, expect } from '@playwright/test'
import type { APIResponse } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { ProblemSchema } from '@/lib/problem'
import { dropPlanner, seedPublishedPlanner } from '../src/plannerFixture'
import { closeSeedPool } from '../src/seed'
import { ACCESS_HEADERS } from '../src/staging'

// Every error body the client can meet is parsed with the schema the client itself parses it
// with, so a filter or advice that answers in some other shape fails here rather than in a
// user's console. Only the fields the client reads are asserted: a body that also carries the
// deploy window's `message` mirror is still a pass.

test.afterAll(closeSeedPool)

/** `rate-limit.sse.capacity` in application.properties: the opens one client gets per window. */
const SSE_BUCKET_CAPACITY = 15

const STREAM_HEADERS = { Accept: 'text/event-stream' }

async function expectProblem(response: APIResponse, status: number, code: string): Promise<void> {
  expect(response.status()).toBe(status)
  const parsed = ProblemSchema.safeParse(await response.json())
  expect(parsed.success, JSON.stringify(parsed)).toBe(true)
  expect(parsed.data?.code).toBe(code)
}

test('the comment stream of a planner nobody published reads as gone', async ({ request }) => {
  const response = await request.get(`/api/planner/${randomUUID()}/comments/events`, {
    headers: STREAM_HEADERS,
  })

  await expectProblem(response, 404, 'PLANNER_NOT_FOUND')
})

test('a planner id that is not a uuid reads as gone', async ({ request }) => {
  const response = await request.get('/api/planner/not-a-uuid/comments/events', {
    headers: STREAM_HEADERS,
  })

  await expectProblem(response, 404, 'NOT_FOUND')
})

test('a route nothing serves answers a problem body', async ({ request }) => {
  const response = await request.get('/api/typo')

  await expectProblem(response, 404, 'NOT_FOUND')
})

test('a write without the csrf header is refused with its own code', async ({ request }) => {
  const response = await request.post('/api/auth/logout')

  await expectProblem(response, 403, 'CSRF_TOKEN_INVALID')
})

test('an unauthenticated read of the session answers a problem body', async ({ request }) => {
  const response = await request.get('/api/auth/me')

  await expectProblem(response, 401, 'UNAUTHORIZED')
})

test('the stream open past the window is refused as a rate limit', async ({ request, baseURL }) => {
  const owner = await seedPublishedPlanner(baseURL!, 'error-bodies')
  const streamPath = `/api/planner/${owner.plannerId}/comments/events`
  // One bucket per client, and an absent device cookie is minted fresh per request: a fixed one
  // spends a bucket of this test's own.
  const headers = { ...ACCESS_HEADERS, ...STREAM_HEADERS, Cookie: `deviceId=${randomUUID()}` }
  const aborter = new AbortController()

  try {
    // The request fixture buffers the body, so it cannot hold a stream open. These opens spend
    // the bucket; their bodies are never read and the abort closes them.
    await Promise.all(
      Array.from({ length: SSE_BUCKET_CAPACITY }, () =>
        fetch(`${baseURL}${streamPath}`, { headers, signal: aborter.signal }),
      ),
    )

    const refused = await request.get(streamPath, { headers })
    await expectProblem(refused, 429, 'RATE_LIMIT_EXCEEDED')
  } finally {
    aborter.abort()
    await dropPlanner(owner)
  }
})
