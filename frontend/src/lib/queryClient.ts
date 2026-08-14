import { QueryClient, QueryCache, MutationCache } from '@tanstack/react-query'

import { ServiceUpdatingError, BackendUnavailableError, RetryableUnavailableError } from './api'
import { showError, showSuccess, showUnavailable } from './errorPresentation'
import {
  STALE_TIME,
  GC_TIME,
  MAX_RETRYABLE_ATTEMPTS,
  RETRY_BASE_MS,
  RETRY_MAX_MS,
} from '@/lib/constants'

declare module '@tanstack/react-query' {
  interface Register {
    mutationMeta: {
      successMessage?: string
      successParams?: Record<string, unknown>
      /** Opt out where the mutation renders its own failure surface. */
      suppressErrorToast?: boolean
    }
  }
}

export const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error) => {
      console.error('Query failed:', error)
      // Queries stay narrow: a thrown query error already reaches the route
      // error component, so toasting anything else would double-report it.
      showUnavailable(error)
    },
  }),
  mutationCache: new MutationCache({
    onError: (error, _variables, _onMutateResult, mutation) => {
      if (mutation.meta?.suppressErrorToast === true) return
      showError(error)
    },
    onSuccess: (_data, _variables, _onMutateResult, mutation) => {
      const message = mutation.meta?.successMessage
      if (message !== undefined) showSuccess(message, mutation.meta?.successParams)
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: STALE_TIME.SHORT,
      gcTime: GC_TIME.SHORT,
      retry: (failureCount, error) => {
        if (error instanceof RetryableUnavailableError) {
          return failureCount < MAX_RETRYABLE_ATTEMPTS
        }
        if (error instanceof ServiceUpdatingError || error instanceof BackendUnavailableError) {
          return false
        }
        return failureCount < 1
      },
      retryDelay: (attempt) => Math.min(RETRY_BASE_MS * 2 ** attempt, RETRY_MAX_MS),
      refetchOnWindowFocus: true,
    },
  },
})
