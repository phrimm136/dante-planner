import { vi } from 'vitest'

import type {
  UseMutateAsyncFunction,
  UseMutateFunction,
  UseMutationResult,
} from '@tanstack/react-query'

/**
 * A complete `useMutation` return value, overridable field by field. A mocked
 * mutation hook has to return the whole shape its component consumes, not the
 * two or three flags the test asserts on.
 */
export function buildMutationResult<
  TData,
  TError = Error,
  TVariables = unknown,
  TContext = unknown,
>(
  overrides: Partial<UseMutationResult<TData, TError, TVariables, TContext>> = {},
): UseMutationResult<TData, TError, TVariables, TContext> {
  const idle: UseMutationResult<TData, TError, TVariables, TContext> = {
    context: undefined,
    data: undefined,
    error: null,
    failureCount: 0,
    failureReason: null,
    isError: false,
    isIdle: true,
    isPaused: false,
    isPending: false,
    isSuccess: false,
    mutate: vi.fn<UseMutateFunction<TData, TError, TVariables, TContext>>(),
    mutateAsync: vi.fn<UseMutateAsyncFunction<TData, TError, TVariables, TContext>>(),
    reset: vi.fn<() => void>(),
    status: 'idle',
    submittedAt: 0,
    variables: undefined,
  }
  return Object.assign({}, idle, overrides)
}
