import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useApiMutation } from '../useApiMutation'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => `t(${key})` }),
}))

vi.mock('@/lib/toast', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

import { toast } from '@/lib/toast'

function createClientAndWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return { queryClient, wrapper }
}

describe('useApiMutation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('invalidates every key returned by invalidateKeys on success', async () => {
    const { queryClient, wrapper } = createClientAndWrapper()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(
      () =>
        useApiMutation<void, { id: string }>({
          mutationFn: async () => {},
          invalidateKeys: ({ id }) => [['planner'], ['comments', id]],
        }),
      { wrapper }
    )

    result.current.mutate({ id: 'p1' })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['planner'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['comments', 'p1'] })
    expect(invalidateSpy).toHaveBeenCalledTimes(2)
  })

  it('runs the onSuccess extension with data, variables, and the query client', async () => {
    const { queryClient, wrapper } = createClientAndWrapper()
    const onSuccess = vi.fn()

    const { result } = renderHook(
      () =>
        useApiMutation<string, number>({
          mutationFn: async (n) => `value-${n}`,
          onSuccess,
        }),
      { wrapper }
    )

    result.current.mutate(7)
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(onSuccess).toHaveBeenCalledWith('value-7', 7, queryClient)
  })

  it('shows a translated success toast when successToastKey is set', async () => {
    const { wrapper } = createClientAndWrapper()

    const { result } = renderHook(
      () =>
        useApiMutation<void>({
          mutationFn: async () => {},
          successToastKey: 'comments.toast.deletedSuccess',
        }),
      { wrapper }
    )

    result.current.mutate()
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(toast.success).toHaveBeenCalledWith('t(comments.toast.deletedSuccess)')
    expect(toast.error).not.toHaveBeenCalled()
  })

  it('logs with the errorLogPrefix and shows the error toast on failure', async () => {
    const { wrapper } = createClientAndWrapper()
    const failure = new Error('boom')

    const { result } = renderHook(
      () =>
        useApiMutation<void>({
          mutationFn: async () => {
            throw failure
          },
          errorLogPrefix: 'Edit comment failed',
          errorToastKey: 'comments.toast.editFailed',
        }),
      { wrapper }
    )

    result.current.mutate()
    await waitFor(() => expect(result.current.isError).toBe(true))

    expect(console.error).toHaveBeenCalledWith('Edit comment failed:', failure)
    expect(toast.error).toHaveBeenCalledWith('t(comments.toast.editFailed)')
  })

  it('an onError override replaces the default log + toast behavior', async () => {
    const { wrapper } = createClientAndWrapper()
    const failure = new Error('conflict')
    const onError = vi.fn()

    const { result } = renderHook(
      () =>
        useApiMutation<void>({
          mutationFn: async () => {
            throw failure
          },
          errorLogPrefix: 'Should not log',
          errorToastKey: 'should.not.toast',
          onError,
        }),
      { wrapper }
    )

    result.current.mutate()
    await waitFor(() => expect(result.current.isError).toBe(true))

    expect(onError).toHaveBeenCalledWith(failure)
    expect(console.error).not.toHaveBeenCalled()
    expect(toast.error).not.toHaveBeenCalled()
  })

  it('stays silent on failure when no error handling is configured', async () => {
    const { wrapper } = createClientAndWrapper()

    const { result } = renderHook(
      () =>
        useApiMutation<void>({
          mutationFn: async () => {
            throw new Error('quiet')
          },
        }),
      { wrapper }
    )

    result.current.mutate()
    await waitFor(() => expect(result.current.isError).toBe(true))

    expect(console.error).not.toHaveBeenCalled()
    expect(toast.error).not.toHaveBeenCalled()
  })
})
