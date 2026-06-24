import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/**
 * MSW request-interception server for the network boundary in tests.
 *
 * Coexistence note: `vitest.setup.ts:59` sets `globalThis.fetch = vi.fn()`
 * unconditionally for every test file. Calling `server.listen()` here patches
 * `globalThis.fetch` with MSW's interceptor, which competes for the same
 * global. To avoid clobbering the `vi.fn()` mock that the broader suite relies
 * on, the lifecycle (`listen`/`resetHandlers`/`close`) is NOT wired globally —
 * each network-level test owns its own `beforeAll(listen)` / `afterAll(close)`
 * scope and restores `globalThis.fetch = vi.fn()` on teardown.
 *
 * @see .claude/rules/frontend/testing.md for the hybrid mocking policy.
 */
export const server = setupServer(...handlers)
