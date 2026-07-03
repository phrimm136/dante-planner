/**
 * API Mutation Skeleton Factory
 *
 * Normalizes the shared shape of this app's useMutation hooks: queryClient
 * plumbing, query-key invalidation on success, i18n toast keys, and
 * console.error logging on failure. Hook-specific semantics (cache writes,
 * side effects, conditional error branches) stay in the wrapping hook via
 * the `onSuccess` extension and the `onError` override.
 */

import {
  useMutation,
  useQueryClient,
  type QueryClient,
  type QueryKey,
  type UseMutationResult,
} from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { toast } from '@/lib/toast'

export interface UseApiMutationOptions<TData, TVariables> {
  mutationFn: (variables: TVariables) => Promise<TData>
  /** Query keys invalidated after a successful mutation */
  invalidateKeys?: (variables: TVariables) => readonly QueryKey[]
  /** i18n key for a success toast, shown after invalidation and the onSuccess extension */
  successToastKey?: string
  /** Prefix for console.error logging on failure; omit for hooks that do not log */
  errorLogPrefix?: string
  /** i18n key for an error toast, shown after logging */
  errorToastKey?: string
  /** Success extension for direct cache updates and side effects */
  onSuccess?: (data: TData, variables: TVariables, queryClient: QueryClient) => void
  /** Full onError override; replaces the default log + toast behavior */
  onError?: (error: Error) => void
}

export function useApiMutation<TData, TVariables = void>(
  options: UseApiMutationOptions<TData, TVariables>
): UseMutationResult<TData, Error, TVariables> {
  const queryClient = useQueryClient()
  const { t } = useTranslation()

  return useMutation({
    mutationFn: options.mutationFn,
    onSuccess: (data, variables) => {
      for (const queryKey of options.invalidateKeys?.(variables) ?? []) {
        void queryClient.invalidateQueries({ queryKey })
      }
      options.onSuccess?.(data, variables, queryClient)
      if (options.successToastKey) {
        toast.success(t(options.successToastKey))
      }
    },
    onError: (error) => {
      if (options.onError) {
        options.onError(error)
        return
      }
      if (options.errorLogPrefix) {
        console.error(`${options.errorLogPrefix}:`, error)
      }
      if (options.errorToastKey) {
        toast.error(t(options.errorToastKey))
      }
    },
  })
}
