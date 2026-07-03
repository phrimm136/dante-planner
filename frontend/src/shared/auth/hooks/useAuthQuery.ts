import { useSuspenseQuery, useQuery, useMutation, useQueryClient, queryOptions } from '@tanstack/react-query';
import { ApiClient, BackendUnavailableError, ServiceUpdatingError } from '@/lib/api';
import { queryClient } from '@/lib/queryClient';
import { UserSchema, type User } from '../schemas/AuthSchemas';

/**
 * Query keys for auth-related queries
 */
export const authQueryKeys = {
  me: ['auth', 'me'] as const,
};

/**
 * Query options for fetching current user
 * Returns null if no auth cookie exists (unauthenticated state)
 */
export function createAuthMeQueryOptions() {
  return queryOptions({
    queryKey: authQueryKeys.me,
    queryFn: async (): Promise<User | null> => {
      try {
        const data = await ApiClient.get<User | null>('/api/auth/me');
        if (data === null) return null;
        const result = UserSchema.safeParse(data);
        if (!result.success) {
          console.error('User validation failed:', result.error);
          return null;
        }
        return result.data;
      } catch (error) {
        // Transient backend/DB unavailability must NOT log the user out: preserve the
        // last-known identity so a maintenance blip doesn't flip an authed user to guest.
        // Only a genuine auth failure (401 / invalid token / null body) degrades to guest.
        if (error instanceof BackendUnavailableError || error instanceof ServiceUpdatingError) {
          const cached = queryClient.getQueryData<User | null>(authQueryKeys.me);
          if (cached) return cached;
        }
        return null;
      }
    },
    staleTime: 5 * 60 * 1000, // 5 minutes - auth state is relatively stable
    retry: false, // Don't retry auth failures
  });
}

/**
 * Hook to get current authenticated user
 * Uses Suspense for SSR-compatible loading states
 *
 * @example
 * ```tsx
 * function Profile() {
 *   const { data: user } = useAuthQuery();
 *
 *   if (!user) {
 *     return <LoginPrompt />;
 *   }
 *
 *   return <div>Welcome, {user.email}</div>;
 * }
 * ```
 */
export function useAuthQuery() {
  return useSuspenseQuery(createAuthMeQueryOptions());
}

/**
 * Non-blocking auth query - does NOT suspend rendering.
 * Use this in components where auth state is optional and should not block page load.
 *
 * Returns isLoading=true while fetching, allowing graceful loading states.
 *
 * @example
 * ```tsx
 * function Header() {
 *   const { data: user, isLoading } = useAuthQueryNonBlocking();
 *
 *   if (isLoading) {
 *     return <HeaderSkeleton />;
 *   }
 *
 *   return user ? <UserMenu user={user} /> : <LoginButton />;
 * }
 * ```
 */
export function useAuthQueryNonBlocking() {
  return useQuery(createAuthMeQueryOptions());
}

/**
 * Hook for logout mutation
 *
 * @example
 * ```tsx
 * function LogoutButton() {
 *   const logout = useLogout();
 *
 *   return (
 *     <button onClick={() => logout.mutate()}>
 *       Logout
 *     </button>
 *   );
 * }
 * ```
 */
export function useLogout() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      await ApiClient.post('/api/auth/logout');
    },
    onSuccess: () => {
      // Clear auth cache
      queryClient.setQueryData(authQueryKeys.me, null);
    },
    onError: (error) => {
      console.error('Logout failed:', error);
    },
  });
}
