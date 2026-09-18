import { QueryClient } from '@tanstack/react-query'

import { createMutationCache, createQueryCache } from '@/lib/queryClient'

export function createTestQueryClient() {
  return new QueryClient({
    queryCache: createQueryCache(),
    mutationCache: createMutationCache(),
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: Infinity,
        staleTime: 0, // Always consider data stale in tests
      },
      mutations: {
        retry: false, // Don't retry failed mutations in tests
      },
    },
  })
}
