import { useSuspenseQuery, useMutation, useQueryClient, queryOptions } from '@tanstack/react-query';
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
        const data = await ApiClient.get<User>('/api/auth/me');
        const result = UserSchema.safeParse(data);
        if (!result.success) {
          console.error('User validation failed:', result.error);
          return null;
        }
        return result.data;
      } catch (error) {
        // No cookie or invalid token = not authenticated
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
