import {
  useMutation,
  useQueryClient,
  type QueryClient,
  type QueryKey,
  type UseMutationResult,
} from '@tanstack/react-query'

export interface UseApiMutationOptions<TData, TVariables> {
  mutationFn: (variables: TVariables) => Promise<TData>
  invalidateKeys?: (variables: TVariables) => readonly QueryKey[]
  successToastKey?: string
  onSuccess?: (data: TData, variables: TVariables, queryClient: QueryClient) => void
  onError?: (error: Error) => void
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
