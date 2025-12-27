import { QueryClient, QueryCache } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error) => {
      // Log errors for debugging
      console.error('Query failed:', error)
      // Note: Toast notifications are disabled for queries
      // useSuspenseQuery throws errors that are caught by RouteErrorComponent
      // This prevents duplicate error displays (toast + error page)
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 60 * 1000, // 1 minute - data is fresh for 1 min
      gcTime: 5 * 60 * 1000, // 5 minutes - cache for 5 min (formerly cacheTime)
      retry: 1, // Retry failed queries once
      refetchOnWindowFocus: false, // Don't refetch when window regains focus
    },
  },
})
