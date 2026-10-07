import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../mswServer'

/** Smoke test proving MSW intercepts a real `fetch` at the network boundary. */
describe('MSW server interception', () => {
  it('returns the registered fixture for an intercepted request', async () => {
    const fixture = { id: 'planner-1', name: 'Intercepted' }
    server.use(
      http.get('http://localhost/api/planners/planner-1', () => HttpResponse.json(fixture)),
    )

    const response = await fetch('http://localhost/api/planners/planner-1')
    const body = await response.json()

    expect(response.ok).toBe(true)
    expect(body).toEqual(fixture)
  })
})
