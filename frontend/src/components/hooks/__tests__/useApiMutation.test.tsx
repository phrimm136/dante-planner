import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useApiMutation } from '../useApiMutation'

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
      { wrapper },
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
      { wrapper },
    )

    result.current.mutate(7)
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(onSuccess).toHaveBeenCalledWith('value-7', 7, queryClient)
  })

  it('declares the success message for the cache to report', async () => {
    const { queryClient, wrapper } = createClientAndWrapper()

    const { result } = renderHook(
      () =>
        useApiMutation<void>({
          mutationFn: async () => {},
          successToastKey: 'common:comments.toast.deletedSuccess',
        }),
      { wrapper },
    )

    result.current.mutate()
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    const [mutation] = queryClient.getMutationCache().getAll()
    expect(mutation).toBeDefined()
    expect(mutation?.meta).toEqual({
      successMessage: 'common:comments.toast.deletedSuccess',
    })
  })

  it('declares no success message when none is configured', async () => {
    const { queryClient, wrapper } = createClientAndWrapper()

    const { result } = renderHook(() => useApiMutation<void>({ mutationFn: async () => {} }), {
      wrapper,
    })

    result.current.mutate()
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    const [mutation] = queryClient.getMutationCache().getAll()
    expect(mutation).toBeDefined()
    expect(mutation?.meta).toEqual({
      successMessage: undefined,
    })
  })

  it('runs the onError extension on failure', async () => {
    const { wrapper } = createClientAndWrapper()
    const failure = new Error('conflict')
    const onError = vi.fn()

    const { result } = renderHook(
      () =>
        useApiMutation<void>({
          mutationFn: async () => {
            throw failure
          },
          onError,
        }),
      { wrapper },
    )

    result.current.mutate()
    await waitFor(() => expect(result.current.isError).toBe(true))

    expect(onError).toHaveBeenCalledWith(failure)
  })

  it('reports nothing itself on failure, leaving that to the cache', async () => {
    const { wrapper } = createClientAndWrapper()

    const { result } = renderHook(
      () =>
        useApiMutation<void>({
          mutationFn: async () => {
            throw new Error('quiet')
          },
        }),
      { wrapper },
    )

    result.current.mutate()
    await waitFor(() => expect(result.current.isError).toBe(true))

    expect(console.error).not.toHaveBeenCalled()
  })
})
