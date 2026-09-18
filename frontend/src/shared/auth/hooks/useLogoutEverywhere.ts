import { useMutation } from '@tanstack/react-query'
import { ApiClient } from '@/lib/api'

export function useLogoutEverywhere() {
  return useMutation({
    mutationFn: async (): Promise<void> => {
      await ApiClient.post('/api/auth/logout-all')
    },
    onError: (error) => {
      console.error('Failed to log out everywhere:', error)
    },
  })
}
