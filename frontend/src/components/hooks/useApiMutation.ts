/**
 * API Mutation Skeleton Factory
 *
 * Normalizes the shared shape of this app's useMutation hooks: queryClient
 * plumbing, query-key invalidation on success, and the success message the
 * mutation cache reports. Hook-specific semantics (cache writes, side effects,
 * conditional error branches) stay in the wrapping hook via the `onSuccess`
 * extension and the `onError` override.
 */

import {
  useMutation,
  useQueryClient,
  type QueryClient,
  type QueryKey,
  type UseMutationResult,
} from '@tanstack/react-query'

export interface UseApiMutationOptions<TData, TVariables> {
  mutationFn: (variables: TVariables) => Promise<TData>
  /** Query keys invalidated after a successful mutation */
  invalidateKeys?: (variables: TVariables) => readonly QueryKey[]
  /** Namespaced i18n key the mutation cache toasts once the mutation succeeds */
  successToastKey?: string
  /** Success extension for direct cache updates and side effects */
  onSuccess?: (data: TData, variables: TVariables, queryClient: QueryClient) => void
  /** Extension for branch-specific failure handling; the cache still reports the error */
  onError?: (error: Error) => void
  /**
   * Hand reporting to `onError` entirely, for a mutation whose copy depends on
   * which resource it acted on — something the failure alone cannot say.
   */
  suppressErrorToast?: boolean
}

export function useApiMutation<TData, TVariables = void>(
  options: UseApiMutationOptions<TData, TVariables>,
): UseMutationResult<TData, Error, TVariables> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: options.mutationFn,
    meta: {
      ...(options.successToastKey !== undefined && { successMessage: options.successToastKey }),
      ...(options.suppressErrorToast === true && { suppressErrorToast: true }),
    },
    onSuccess: (data, variables) => {
      for (const queryKey of options.invalidateKeys?.(variables) ?? []) {
        void queryClient.invalidateQueries({ queryKey })
      }
      options.onSuccess?.(data, variables, queryClient)
    },
    onError: (error) => {
      options.onError?.(error)
    },
  })
}
