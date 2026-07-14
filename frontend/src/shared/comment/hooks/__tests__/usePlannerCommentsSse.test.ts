import { createElement } from 'react'
import { renderHook, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'

/**
 * Characterization test for usePlannerCommentsSse — the recipient-update seam:
 * a `comment:added` SSE event carrying a comment payload appends that comment to
 * the comment tree cache via setQueryData (no refetch, no invalidate).
 *
 * Only the leaf boundaries are doubled: ApiClient.createEventSource returns a
 * controllable MockEventSource, and the SSE envelope schema is a pass-through so
 * the handler sees the raw payload verbatim.
 */

const h = vi.hoisted(() => {
  class MockEventSource {
    static instances: MockEventSource[] = []
    url: string
    onerror: (() => void) | null = null
    listeners: Record<string, ((e: { data: string }) => void)[]> = {}
    closed = false
    constructor(url: string) {
      this.url = url
      MockEventSource.instances.push(this)
    }
    addEventListener(type: string, cb: (e: { data: string }) => void) {
      ;(this.listeners[type] ??= []).push(cb)
    }
    close() {
      this.closed = true
    }
    emit(type: string, data: unknown) {
      ;(this.listeners[type] ?? []).forEach((cb) => cb({ data: JSON.stringify(data) }))
    }
  }
  return { MockEventSource }
})

vi.mock('@/lib/api', () => ({
  ApiClient: {
    createEventSource: (path: string) => new h.MockEventSource(path),
  },
}))

vi.mock('@/shared/sse', () => ({
  SseEnvelopeSchema: {
    parse: (x: unknown) => x,
    safeParse: (x: unknown) => ({ success: true, data: x }),
  },
}))

import { usePlannerCommentsSse } from '../usePlannerCommentsSse'

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  })
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
  return { queryClient, wrapper }
}

function lastEventSource() {
  const list = h.MockEventSource.instances
  return list[list.length - 1]
}

beforeEach(() => {
  h.MockEventSource.instances = []
})

describe('usePlannerCommentsSse — comment tree cache patch', () => {
  it('comment:added event with a comment payload appends the comment to the tree cache', () => {
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(
      ['comments', 'planner-1'],
      [{ id: 'c1', content: 'first', replies: [] }],
    )

    renderHook(() => usePlannerCommentsSse('planner-1'), { wrapper })
    const es = lastEventSource()

    act(() =>
      es.emit('comment:added', {
        type: 'created',
        plannerId: 'planner-1',
        payload: { id: 'c2', content: 'second', replies: [] },
      }),
    )

    const tree = queryClient.getQueryData(['comments', 'planner-1']) as Array<{
      id: string
      content: string
    }>
    expect(tree).toContainEqual(expect.objectContaining({ id: 'c1' }))
    expect(tree).toContainEqual(expect.objectContaining({ id: 'c2', content: 'second' }))
  })
})
