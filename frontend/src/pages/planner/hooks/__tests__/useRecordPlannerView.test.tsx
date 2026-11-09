import { StrictMode } from 'react'
import { describe, it, expect, beforeEach } from 'vitest'
import { render, renderHook, screen, waitFor } from '@testing-library/react'
import { ErrorBoundary } from 'react-error-boundary'
import { http, HttpResponse } from 'msw'

import { server } from '@/test-utils/mswServer'
import { queryClient } from '@/lib/queryClient'
import { loadPublishedPlannerRoute } from '@/lib/routeLoaders'
import { useRecordPlannerView } from '../useRecordPlannerView'
import { publishedPayload } from './publishedPlannerPayload'

const API = 'http://localhost:8080/api/planner/md'

let viewPosts: string[] = []

function recordViewPosts(status = 204) {
  server.use(
    http.post(`${API}/published/:id/viewcount`, ({ params }) => {
      viewPosts.push(String(params.id))
      return new HttpResponse(null, { status })
    }),
  )
}

function settle() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

beforeEach(() => {
  viewPosts = []
  queryClient.clear()
})

describe('useRecordPlannerView', () => {
  it('posts one view for the mounted planner', async () => {
    recordViewPosts()

    renderHook(() => useRecordPlannerView('planner-x'))

    await waitFor(() => expect(viewPosts).toEqual(['planner-x']))
    await settle()
    expect(viewPosts).toEqual(['planner-x'])
  })

  it('posts one view under StrictMode double effects', async () => {
    recordViewPosts()

    renderHook(() => useRecordPlannerView('planner-x'), { wrapper: StrictMode })

    await waitFor(() => expect(viewPosts).toEqual(['planner-x']))
    await settle()
    expect(viewPosts).toEqual(['planner-x'])
  })

  it('posts one view for the next planner when the id changes', async () => {
    recordViewPosts()

    const { rerender } = renderHook(({ id }) => useRecordPlannerView(id), {
      initialProps: { id: 'planner-x' },
      wrapper: StrictMode,
    })
    await waitFor(() => expect(viewPosts).toEqual(['planner-x']))

    rerender({ id: 'planner-y' })

    await waitFor(() => expect(viewPosts).toEqual(['planner-x', 'planner-y']))
    await settle()
    expect(viewPosts).toEqual(['planner-x', 'planner-y'])
  })

  it('posts again when the page remounts', async () => {
    recordViewPosts()

    const first = renderHook(() => useRecordPlannerView('planner-x'))
    await waitFor(() => expect(viewPosts).toEqual(['planner-x']))
    first.unmount()

    renderHook(() => useRecordPlannerView('planner-x'))

    await waitFor(() => expect(viewPosts).toEqual(['planner-x', 'planner-x']))
  })

  it('posts nothing while no published planner is known', async () => {
    recordViewPosts()

    renderHook(() => useRecordPlannerView(null))

    await settle()
    expect(viewPosts).toEqual([])
  })

  it('keeps the page rendered when the endpoint fails', async () => {
    recordViewPosts(500)

    function Page() {
      useRecordPlannerView('planner-x')
      return <p>planner content</p>
    }
    render(
      <ErrorBoundary fallback={<p>error fallback</p>}>
        <Page />
      </ErrorBoundary>,
    )

    await waitFor(() => expect(viewPosts).toEqual(['planner-x']))
    await settle()
    expect(screen.getByText('planner content')).toBeInTheDocument()
    expect(screen.queryByText('error fallback')).toBeNull()
    expect(viewPosts).toEqual(['planner-x'])
  })

  it('posts nothing from the detail route loader', async () => {
    recordViewPosts()
    server.use(http.get(`${API}/published/:id`, () => HttpResponse.json(publishedPayload)))

    const loaded = await loadPublishedPlannerRoute({
      params: { id: publishedPayload.id },
      abortController: new AbortController(),
    })

    expect(loaded).toEqual({ title: publishedPayload.title })
    await settle()
    expect(viewPosts).toEqual([])
  })
})
