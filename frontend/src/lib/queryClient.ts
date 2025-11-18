import { QueryClient, QueryCache } from '@tanstack/react-query'
import { toast } from 'sonner'

export const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error) => {
      console.error('Query failed:', error)
      toast.error(`Failed to load data: ${error.message}`)
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
