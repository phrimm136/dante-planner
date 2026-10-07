import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/**
 * MSW request-interception server for the network boundary in tests.
 *
 * `vitest.setup.ts` owns its lifecycle for every test file and fails any request
 * no handler matches; a test adds its handlers with `server.use(...)`. A test
 * that spies on `fetch` installs the spy over MSW's patched global, which the
 * suite's `restoreMocks` hands back after each test.
 */
export const server = setupServer(...handlers)
