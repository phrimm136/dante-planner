import { QueryClient } from '@tanstack/react-query'

/**
 * Creates a QueryClient configured for testing
 * - Disables retries to avoid test timeouts
 * - Sets gcTime to Infinity to prevent "tests didn't exit" errors
 * - Sets staleTime to 0 so data is always refetched in tests
 */
export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,      // Don't retry failed queries in tests
        gcTime: Infinity,  // Prevent premature garbage collection
        staleTime: 0,      // Always consider data stale in tests
      },
      mutations: {
        retry: false,      // Don't retry failed mutations in tests
      },
    },
  })
}
