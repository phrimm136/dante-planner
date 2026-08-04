import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'

/**
 * The bookmark endpoint keeps a legacy toggle path for bodyless requests, so the
 * request body is the contract worth pinning: a bodyless POST silently flips the
 * bookmark instead of driving it to the state the caller asked for.
 */

const h = vi.hoisted(() => ({ post: vi.fn() }))

vi.mock('@/lib/api', () => ({ ApiClient: { post: h.post } }))

import { usePlannerBookmark } from '../usePlannerBookmark'

const PLANNER_ID = '11111111-1111-4111-8111-111111111111'

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return { queryClient, wrapper }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('usePlannerBookmark', () => {
  it.each([true, false])('names the desired state %s in the request body', async (bookmarked) => {
    h.post.mockResolvedValue({ plannerId: PLANNER_ID, bookmarked })
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => usePlannerBookmark(), { wrapper })

    act(() => result.current.mutate({ plannerId: PLANNER_ID, bookmarked }))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(h.post).toHaveBeenCalledWith(`/api/planner/md/${PLANNER_ID}/bookmark`, { bookmarked })
  })

  it('rejects a response that is not the bookmark shape', async () => {
    h.post.mockResolvedValue({ plannerId: PLANNER_ID })
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => usePlannerBookmark(), { wrapper })
    vi.spyOn(console, 'error').mockImplementation(() => {})

    act(() => result.current.mutate({ plannerId: PLANNER_ID, bookmarked: true }))

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
