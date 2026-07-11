import { QueryClient, QueryCache, MutationCache } from '@tanstack/react-query'

import { ServiceUpdatingError, BackendUnavailableError, AuthTemporarilyUnavailableError } from './api'
import { toast } from './toast'
import i18n from './i18n'

export function handleBackendDownError(error: Error): void {
  if (error instanceof ServiceUpdatingError) {
    toast.error(i18n.t('errors.serviceUpdating'))
  } else if (error instanceof BackendUnavailableError) {
    toast.error(i18n.t('errors.backendUnavailable'))
  } else if (error instanceof AuthTemporarilyUnavailableError) {
    toast.error(i18n.t('errors.authUnavailable'))
  }
}

export const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error) => {
      // Log errors for debugging
      console.error('Query failed:', error)
      handleBackendDownError(error)
      // Note: Other toast notifications are disabled for queries
      // useSuspenseQuery throws errors that are caught by RouteErrorComponent
      // This prevents duplicate error displays (toast + error page)
    },
  }),
  mutationCache: new MutationCache({
    onError: (error) => {
      handleBackendDownError(error)
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 60 * 1000, // 1 minute - data is fresh for 1 min
      gcTime: 5 * 60 * 1000, // 5 minutes - cache for 5 min (formerly cacheTime)
      retry: (failureCount, error) => {
        if (error instanceof ServiceUpdatingError || error instanceof BackendUnavailableError) {
          return false
        }
        return failureCount < 1
      },
      refetchOnWindowFocus: false, // Don't refetch when window regains focus
    },
  },
})
