import { createElement } from 'react'
import { renderHook, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'

/**
 * Characterization test for usePlannerCommentsSse — the recipient-update seam:
 * a `comment:added` SSE event carrying a comment payload appends that comment to
 * the comment tree cache via setQueryData (no refetch, no invalidate).
 *
 * Only the leaf boundaries are doubled: `fetch` answers the stream request with
 * a body the test feeds frames into, and the SSE envelope schema is a
 * pass-through so the handler sees the raw payload verbatim.
 */

vi.mock('@/shared/sse', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/sse')>()),
  SseEnvelopeSchema: {
    parse: (x: unknown) => x,
    safeParse: (x: unknown) => ({ success: true, data: x }),
  },
}))

const presenterMocks = vi.hoisted(() => ({ showErrorMessage: vi.fn() }))
vi.mock('@/lib/errorPresentation', () => presenterMocks)
vi.mock('@/lib/i18n', () => ({ default: { t: (key: string) => key } }))

import { usePlannerCommentsSse } from '../usePlannerCommentsSse'

const encoder = new TextEncoder()

interface StreamHandle {
  push: (chunk: string) => void
}

const streams: StreamHandle[] = []
const requestUrls: string[] = []

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  })
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
  return { queryClient, wrapper }
}

/** Flush the promise chain the fetch transport runs on. */
async function settle() {
  await act(async () => {
    for (let i = 0; i < 20; i++) {
      await Promise.resolve()
    }
  })
}

async function mountStream(
  plannerId: string,
  wrapper: ReturnType<typeof createWrapper>['wrapper'],
) {
  const rendered = renderHook(() => usePlannerCommentsSse(plannerId), { wrapper })
  await settle()
  return { ...rendered, stream: streams[streams.length - 1] }
}

async function emit(stream: StreamHandle, type: string, data: unknown) {
  stream.push(`event: ${type}\ndata: ${JSON.stringify(data)}\n\n`)
  await settle()
}

beforeEach(() => {
  streams.length = 0
  requestUrls.length = 0
  vi.mocked(globalThis.fetch).mockImplementation((input: unknown) => {
    requestUrls.push(String(input))
    let controller: ReadableStreamDefaultController<Uint8Array> | null = null
    const body = new ReadableStream<Uint8Array>({
      start(c) {
        controller = c
      },
    })
    streams.push({ push: (chunk) => controller?.enqueue(encoder.encode(chunk)) })
    return Promise.resolve({
      ok: true,
      status: 200,
      headers: new Headers(),
      body,
    } as unknown as Response)
  })
})

describe('usePlannerCommentsSse — comment tree cache patch', () => {
  it('subscribes to the planner comment stream', async () => {
    const { wrapper } = createWrapper()
    await mountStream('planner-1', wrapper)

    expect(requestUrls[0]).toContain('/api/planner/planner-1/comments/events')
  })

  it('comment:added event with a comment payload appends the comment to the tree cache', async () => {
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(
      ['comments', 'planner-1'],
      [{ id: 'c1', content: 'first', replies: [] }],
    )

    const { stream } = await mountStream('planner-1', wrapper)
    await emit(stream, 'comment:added', {
      type: 'created',
      plannerId: 'planner-1',
      payload: { id: 'c2', content: 'second', replies: [] },
    })

    const tree = queryClient.getQueryData(['comments', 'planner-1']) as Array<{
      id: string
      content: string
    }>
    expect(tree).toContainEqual(expect.objectContaining({ id: 'c1' }))
    expect(tree).toContainEqual(expect.objectContaining({ id: 'c2', content: 'second' }))
  })

  it('a redelivered comment:added counts once and appends once', async () => {
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(['comments', 'planner-1'], [])

    const { result, stream } = await mountStream('planner-1', wrapper)
    const event = {
      type: 'created',
      plannerId: 'planner-1',
      payload: { id: 'c9', content: 'only once', replies: [] },
    }

    await emit(stream, 'comment:added', event)
    await emit(stream, 'comment:added', event)

    const tree = queryClient.getQueryData(['comments', 'planner-1']) as Array<{ id: string }>
    expect(tree.filter((c) => c.id === 'c9')).toHaveLength(1)
    expect(result.current.newCommentsCount).toBe(1)
  })

  it('a reply lands under its parent, not at the top level', async () => {
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(
      ['comments', 'planner-1'],
      [{ id: 'c1', content: 'root', replies: [] }],
    )

    const { stream } = await mountStream('planner-1', wrapper)
    await emit(stream, 'comment:added', {
      type: 'comment:added',
      plannerId: 'planner-1',
      payload: { id: 'r1', parentCommentId: 'c1', content: 'reply', replies: [] },
    })

    const tree = queryClient.getQueryData(['comments', 'planner-1']) as Array<{
      id: string
      replies: Array<{ id: string }>
    }>
    expect(tree).toHaveLength(1)
    expect(tree[0].replies).toContainEqual(expect.objectContaining({ id: 'r1' }))
  })

  it('a nested reply lands under a parent that is itself a reply', async () => {
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(
      ['comments', 'planner-1'],
      [{ id: 'c1', content: 'root', replies: [{ id: 'r1', content: 'reply', replies: [] }] }],
    )

    const { stream } = await mountStream('planner-1', wrapper)
    await emit(stream, 'comment:added', {
      type: 'comment:added',
      plannerId: 'planner-1',
      payload: { id: 'r2', parentCommentId: 'r1', content: 'nested', replies: [] },
    })

    const tree = queryClient.getQueryData(['comments', 'planner-1']) as Array<{
      replies: Array<{ id: string; replies: Array<{ id: string }> }>
    }>
    expect(tree[0].replies[0].replies).toContainEqual(expect.objectContaining({ id: 'r2' }))
  })

  it('a reply to a parent outside the loaded tree only moves the counter', async () => {
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(
      ['comments', 'planner-1'],
      [{ id: 'c1', content: 'root', replies: [] }],
    )

    const { result, stream } = await mountStream('planner-1', wrapper)
    await emit(stream, 'comment:added', {
      type: 'comment:added',
      plannerId: 'planner-1',
      payload: { id: 'r9', parentCommentId: 'unloaded', content: 'orphan', replies: [] },
    })

    expect(queryClient.getQueryData(['comments', 'planner-1'])).toEqual([
      { id: 'c1', content: 'root', replies: [] },
    ])
    expect(result.current.newCommentsCount).toBe(1)
  })
})

describe('usePlannerCommentsSse — planner removed elsewhere', () => {
  it('answers a 404 open with the removal message', async () => {
    // This path names one planner, so its 404 is the planner itself going away.
    vi.mocked(globalThis.fetch).mockImplementation(
      () =>
        Promise.resolve({
          ok: false,
          status: 404,
          headers: new Headers(),
          body: null,
        } as unknown as Response) as Promise<Response>,
    )
    const { wrapper } = createWrapper()

    renderHook(() => usePlannerCommentsSse('planner-1'), { wrapper })
    await settle()

    expect(presenterMocks.showErrorMessage).toHaveBeenCalledTimes(1)
    expect(presenterMocks.showErrorMessage).toHaveBeenCalledWith('planner:sync.removedOnAnotherDevice')
  })
})
