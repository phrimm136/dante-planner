import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'

import { server } from '@/test-utils/mswServer'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { buildSaveablePlanner } from '@/test-utils/fixtures'
import { toUpsertRequest } from '../usePlannerSyncAdapter'
import { usePlannerPublish } from '../usePlannerPublish'

const presentation = vi.hoisted(() => ({
  showError: vi.fn(),
  showErrorMessage: vi.fn(),
}))
vi.mock('@/lib/errorPresentation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/errorPresentation')>()),
  showError: presentation.showError,
  showErrorMessage: presentation.showErrorMessage,
}))
vi.mock('@/shared/notifications', () => ({
  requestNotificationPermission: vi.fn().mockResolvedValue(undefined),
}))

const planner = buildSaveablePlanner({
  metadata: { title: 'Local Edit', status: 'saved', syncVersion: 3 },
})
const plannerId = planner.metadata.id

function serverPlanner(overrides: Record<string, unknown>) {
  return {
    id: plannerId,
    title: 'Local Edit',
    category: '5F',
    status: 'saved',
    content: JSON.stringify(planner.content),
    schemaVersion: 1,
    contentVersion: 6,
    plannerType: 'MIRROR_DUNGEON',
    syncVersion: 4,
    published: true,
    deviceId: null,
    createdAt: '2026-01-01T00:00:00Z',
    lastModifiedAt: '2026-01-01T00:00:00Z',
    savedAt: '2026-01-01T00:00:00Z',
    upvotes: 0,
    ...overrides,
  }
}

interface Seen {
  method: string
  path: string
  body: string
}

let seen: Seen[] = []

function renderPublish() {
  const queryClient = createTestQueryClient()
  return renderHook(() => usePlannerPublish(), {
    wrapper: ({ children }: { children: React.ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  })
}

describe('usePlannerPublish', () => {
  beforeAll(() => {
    server.listen({ onUnhandledRequest: 'error' })
    server.events.on('request:start', ({ request }) => {
      const clone = request.clone()
      void clone.text().then((body) => {
        seen.push({ method: request.method, path: new URL(request.url).pathname, body })
      })
    })
  })

  beforeEach(() => {
    seen = []
    vi.clearAllMocks()
  })

  afterEach(() => {
    server.resetHandlers()
  })

  afterAll(() => {
    server.events.removeAllListeners()
    server.close()
    globalThis.fetch = vi.fn() as unknown as typeof fetch
  })

  it('publishes in one request that carries the save body', async () => {
    server.use(
      http.post('*/api/planner/md/:id/publish', () => HttpResponse.json(serverPlanner({}))),
    )
    const { result } = renderPublish()

    const outcome = await result.current.mutateAsync({ intent: 'publish', planner })

    await waitFor(() => expect(seen).toHaveLength(1))
    expect(seen[0]).toMatchObject({ method: 'POST', path: `/api/planner/md/${plannerId}/publish` })
    expect(JSON.parse(seen[0]!.body)).toEqual(toUpsertRequest(planner))
    expect(outcome.published).toBe(true)
    expect(outcome.acknowledged?.metadata.syncVersion).toBe(4)
  })

  it('unpublishes in one request with no body', async () => {
    server.use(
      http.post('*/api/planner/md/:id/unpublish', () =>
        HttpResponse.json(serverPlanner({ published: false })),
      ),
    )
    const { result } = renderPublish()

    const outcome = await result.current.mutateAsync({ intent: 'unpublish', plannerId })

    await waitFor(() => expect(seen).toHaveLength(1))
    expect(seen[0]).toEqual({
      method: 'POST',
      path: `/api/planner/md/${plannerId}/unpublish`,
      body: '',
    })
    expect(outcome).toEqual({ published: false, acknowledged: null })
  })

  it('presents a publish conflict as the sync failure it was before, not the generic toast', async () => {
    server.use(
      http.post('*/api/planner/md/:id/publish', () =>
        HttpResponse.json(
          { code: 'SYNC_CONFLICT', detail: 'Planner was modified', serverVersion: 5 },
          { status: 409 },
        ),
      ),
    )
    const { result } = renderPublish()

    result.current.mutate({ intent: 'publish', planner })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(presentation.showErrorMessage).toHaveBeenCalledWith('planner:sync.changedElsewhere')
    expect(presentation.showErrorMessage).toHaveBeenCalledTimes(1)
    expect(presentation.showError).not.toHaveBeenCalled()
  })

  it('reports an unpublish failure once through the generic error presentation', async () => {
    server.use(
      http.post('*/api/planner/md/:id/unpublish', () =>
        HttpResponse.json({ code: 'PLANNER_FORBIDDEN', detail: 'Forbidden' }, { status: 403 }),
      ),
    )
    const { result } = renderPublish()

    result.current.mutate({ intent: 'unpublish', plannerId })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(presentation.showError).toHaveBeenCalledTimes(1)
    expect(presentation.showErrorMessage).not.toHaveBeenCalled()
  })
})
