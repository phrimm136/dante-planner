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

vi.mock('@/shared/sse', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/sse')>()),
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

  it('a redelivered comment:added counts once and appends once', () => {
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(['comments', 'planner-1'], [])

    const { result } = renderHook(() => usePlannerCommentsSse('planner-1'), { wrapper })
    const es = lastEventSource()
    const event = {
      type: 'created',
      plannerId: 'planner-1',
      payload: { id: 'c9', content: 'only once', replies: [] },
    }

    act(() => es.emit('comment:added', event))
    act(() => es.emit('comment:added', event))

    const tree = queryClient.getQueryData(['comments', 'planner-1']) as Array<{ id: string }>
    expect(tree.filter((c) => c.id === 'c9')).toHaveLength(1)
    expect(result.current.newCommentsCount).toBe(1)
  })

  it('a reply lands under its parent, not at the top level', () => {
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(
      ['comments', 'planner-1'],
      [{ id: 'c1', content: 'root', replies: [] }],
    )

    renderHook(() => usePlannerCommentsSse('planner-1'), { wrapper })
    const es = lastEventSource()

    act(() =>
      es.emit('comment:added', {
        type: 'comment:added',
        plannerId: 'planner-1',
        payload: { id: 'r1', parentCommentId: 'c1', content: 'reply', replies: [] },
      }),
    )

    const tree = queryClient.getQueryData(['comments', 'planner-1']) as Array<{
      id: string
      replies: Array<{ id: string }>
    }>
    expect(tree).toHaveLength(1)
    expect(tree[0].replies).toContainEqual(expect.objectContaining({ id: 'r1' }))
  })

  it('a nested reply lands under a parent that is itself a reply', () => {
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(
      ['comments', 'planner-1'],
      [{ id: 'c1', content: 'root', replies: [{ id: 'r1', content: 'reply', replies: [] }] }],
    )

    renderHook(() => usePlannerCommentsSse('planner-1'), { wrapper })
    const es = lastEventSource()

    act(() =>
      es.emit('comment:added', {
        type: 'comment:added',
        plannerId: 'planner-1',
        payload: { id: 'r2', parentCommentId: 'r1', content: 'nested', replies: [] },
      }),
    )

    const tree = queryClient.getQueryData(['comments', 'planner-1']) as Array<{
      replies: Array<{ id: string; replies: Array<{ id: string }> }>
    }>
    expect(tree[0].replies[0].replies).toContainEqual(expect.objectContaining({ id: 'r2' }))
  })

  it('a reply to a parent outside the loaded tree only moves the counter', () => {
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(
      ['comments', 'planner-1'],
      [{ id: 'c1', content: 'root', replies: [] }],
    )

    const { result } = renderHook(() => usePlannerCommentsSse('planner-1'), { wrapper })
    const es = lastEventSource()

    act(() =>
      es.emit('comment:added', {
        type: 'comment:added',
        plannerId: 'planner-1',
        payload: { id: 'r9', parentCommentId: 'unloaded', content: 'orphan', replies: [] },
      }),
    )

    expect(queryClient.getQueryData(['comments', 'planner-1'])).toEqual([
      { id: 'c1', content: 'root', replies: [] },
    ])
    expect(result.current.newCommentsCount).toBe(1)
  })
})
