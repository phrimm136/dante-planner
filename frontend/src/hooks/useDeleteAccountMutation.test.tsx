import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useDeleteAccountMutation } from './useUserSettingsQuery'
import { ApiClient } from '@/lib/api'

// Mock ApiClient
vi.mock('@/lib/api', () => ({
  ApiClient: {
    delete: vi.fn(),
  },
}))

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

describe('useDeleteAccountMutation', () => {
  const mockDeleteResponse = {
    message: 'Account scheduled for deletion',
    deletedAt: '2026-01-09T10:00:00Z',
    permanentDeleteAt: '2026-02-08T10:00:00Z',
    gracePeriodDays: 30,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('calls DELETE endpoint with correct type parameter', async () => {
    vi.mocked(ApiClient.delete).mockResolvedValue(mockDeleteResponse)

    const { result } = renderHook(() => useDeleteAccountMutation(), {
      wrapper: createWrapper(),
    })

    result.current.mutate()

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(ApiClient.delete).toHaveBeenCalledWith('/api/user/me')
    expect(ApiClient.delete).toHaveBeenCalledTimes(1)
  })

  it('validates response with UserDeletionResponseSchema', async () => {
    vi.mocked(ApiClient.delete).mockResolvedValue(mockDeleteResponse)

    const { result } = renderHook(() => useDeleteAccountMutation(), {
      wrapper: createWrapper(),
    })

    result.current.mutate()

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(mockDeleteResponse)
  })

  it('throws error when response validation fails', async () => {
    const invalidResponse = {
      message: 'Account deleted',
      // Missing required fields
    }

    vi.mocked(ApiClient.delete).mockResolvedValue(invalidResponse)

    const { result } = renderHook(() => useDeleteAccountMutation(), {
      wrapper: createWrapper(),
    })

    result.current.mutate()

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(console.error).toHaveBeenCalledWith(
      'User deletion response validation failed:',
      expect.anything()
    )
  })

  it('returns deletion response on success', async () => {
    vi.mocked(ApiClient.delete).mockResolvedValue(mockDeleteResponse)

    const { result } = renderHook(() => useDeleteAccountMutation(), {
      wrapper: createWrapper(),
    })

    result.current.mutate()

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    // Verify response data is returned
    expect(result.current.data).toEqual(mockDeleteResponse)
  })

  it('logs error on failure', async () => {
    const error = new Error('Network error')
    vi.mocked(ApiClient.delete).mockRejectedValue(error)

    const { result } = renderHook(() => useDeleteAccountMutation(), {
      wrapper: createWrapper(),
    })

    result.current.mutate()

    await waitFor(() => expect(result.current.isError).toBe(true))

    expect(console.error).toHaveBeenCalledWith('Failed to delete account:', error)
  })

  it('exposes isPending state during mutation', async () => {
    let resolveDelete: (value: any) => void
    const deletePromise = new Promise((resolve) => {
      resolveDelete = resolve
    })
    vi.mocked(ApiClient.delete).mockReturnValue(deletePromise)

    const { result } = renderHook(() => useDeleteAccountMutation(), {
      wrapper: createWrapper(),
    })

    expect(result.current.isPending).toBe(false)

    result.current.mutate()

    await waitFor(() => expect(result.current.isPending).toBe(true))

    resolveDelete!(mockDeleteResponse)

    await waitFor(() => expect(result.current.isPending).toBe(false))
  })
})
