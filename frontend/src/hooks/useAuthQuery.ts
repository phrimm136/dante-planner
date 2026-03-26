import { useSuspenseQuery, useQuery, useMutation, useQueryClient, queryOptions } from '@tanstack/react-query';
import { ApiClient } from '@/lib/api';
import { UserSchema, type User } from '@/schemas/AuthSchemas';

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
function createAuthMeQueryOptions() {
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
      } catch {
        // Auth failure degrades to guest state — never crash the page
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
 * Login credentials with PKCE code verifier
 */
export interface LoginCredentials {
  code: string;
  codeVerifier: string;
}

/**
 * Hook for Google OAuth login mutation with PKCE support
 *
 * @example
 * ```tsx
 * function GoogleCallback() {
 *   const login = useLogin();
 *
 *   useEffect(() => {
 *     const params = new URLSearchParams(window.location.search);
 *     const code = params.get('code');
 *     const codeVerifier = getStoredCodeVerifier();
 *
 *     if (code && codeVerifier) {
 *       login.mutate({ code, codeVerifier });
 *     }
 *   }, []);
 * }
 * ```
 */
export function useLogin() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (credentials: LoginCredentials): Promise<User> => {
      const data = await ApiClient.post<User>('/api/auth/google/callback', {
        code: credentials.code,
        codeVerifier: credentials.codeVerifier,
        provider: 'google',
      });

      const result = UserSchema.safeParse(data);
      if (!result.success) {
        console.error('User validation failed:', result.error);
        throw new Error('Invalid user data received from server');
      }
      return result.data;
    },
    onSuccess: (user) => {
      // Update auth query cache
      queryClient.setQueryData(authQueryKeys.me, user);
    },
    onError: (error) => {
      console.error('Login failed:', error);
    },
  });
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
