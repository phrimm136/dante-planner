import type { QueryClient } from '@tanstack/react-query'
import { onUnauthorized } from '@/lib/api'
import { authQueryKeys } from '../hooks/useAuthQuery'

export function clearSessionOnUnauthorized(queryClient: QueryClient): () => void {
  return onUnauthorized(() => {
    queryClient.setQueryData(authQueryKeys.me, null)
  })
}
