import { http, HttpResponse } from 'msw'

/**
 * Default MSW request handlers registered at server start.
 *
 * Keep this list minimal. Per-test handlers should be registered with
 * `server.use(...)` inside the test that needs them, so the network contract
 * stays local to the behavior under test.
 *
 * @see .claude/rules/frontend/testing.md for the hybrid mocking policy.
 */
export const handlers = [
  // Warning: use absolute URLs — Node's fetch (test runtime) rejects relative
  // paths, so a relative pattern here would never match and silently never fire.
  http.get('http://localhost:8080/api/health', () => HttpResponse.json({ status: 'ok' })),
]
