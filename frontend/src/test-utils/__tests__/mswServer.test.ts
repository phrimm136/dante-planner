import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../mswServer'

/**
 * Smoke test proving MSW intercepts a real `fetch` at the network boundary.
 *
 * Lifecycle is scoped to this file so it does not disturb the suite-wide
 * `globalThis.fetch = vi.fn()` mock (vitest.setup.ts:59). `server.listen()`
 * patches `globalThis.fetch`; `afterAll` restores the vi mock.
 */
describe('MSW server interception', () => {
  beforeAll(() => {
    server.listen({ onUnhandledRequest: 'bypass' })
  })

  afterEach(() => {
    server.resetHandlers()
  })

  afterAll(() => {
    server.close()
    globalThis.fetch = vi.fn() as unknown as typeof fetch
  })

  it('returns the registered fixture for an intercepted request', async () => {
    const fixture = { id: 'planner-1', name: 'Intercepted' }
    server.use(
      http.get('http://localhost/api/planners/planner-1', () =>
        HttpResponse.json(fixture)
      )
    )

    const response = await fetch('http://localhost/api/planners/planner-1')
    const body = await response.json()

    expect(response.ok).toBe(true)
    expect(body).toEqual(fixture)
  })
})
