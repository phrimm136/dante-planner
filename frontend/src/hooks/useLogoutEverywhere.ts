import { useMutation } from '@tanstack/react-query'
import { ApiClient } from '@/lib/api'

/**
 * Hook for the "log out everywhere" mutation.
 *
 * POSTs to the logout-all endpoint, which invalidates every token for the
 * current user across all devices and clears auth cookies (204 No Content).
 * Toast, auth-cache invalidation, and redirect are handled by the calling
 * component, mirroring {@link useDeleteAccountMutation}.
 *
 * @example
 * ```tsx
 * function LogoutEverywhereButton() {
 *   const logoutEverywhere = useLogoutEverywhere();
 *
 *   return (
 *     <button
 *       onClick={() => logoutEverywhere.mutate()}
 *       disabled={logoutEverywhere.isPending}
 *     >
 *       Log out everywhere
 *     </button>
 *   );
 * }
 * ```
 */
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
