import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'

import { useServerStatusStore } from '@/lib/banner/serverStatus'
import { STALE_TIME } from '@/lib/constants'
import { queryClient as appQueryClient } from '@/lib/queryClient'
import { server } from '@/test-utils/mswServer'
import { createTestQueryClient } from '@/test-utils/queryClient'

import { useUpdateAvailable } from '../useUpdateAvailable'

declare const __BUILD_ID__: string

const NEW_BUILD_ID = 'build-next'

const versionRequests = { count: 0 }

function serveVersion(resolver: () => Response) {
  server.use(
    http.get('*/version.json', () => {
      versionRequests.count += 1
      return resolver()
    }),
  )
}

function renderSignal(client: QueryClient = createTestQueryClient()) {
  const view = renderHook(() => useUpdateAvailable(), {
    wrapper: ({ children }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    ),
  })
  return { ...view, client }
}

function settledWith(client: QueryClient, status: 'success' | 'error') {
  return waitFor(() =>
    expect(
      client.getQueryCache().findAll({ predicate: (q) => q.state.status === status }),
    ).toHaveLength(1),
  )
}

describe('useUpdateAvailable', () => {
  beforeAll(() => {
    server.listen({ onUnhandledRequest: 'error' })
  })

  beforeEach(() => {
    vi.stubEnv('DEV', false)
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    versionRequests.count = 0
    useServerStatusStore.setState({ status: 'ok' })
  })

  afterEach(() => {
    server.resetHandlers()
    vi.unstubAllEnvs()
    vi.restoreAllMocks()
  })

  afterAll(() => {
    server.close()
    globalThis.fetch = vi.fn() as unknown as typeof fetch
  })

  it('returns the deployed build id when it differs from the running one', async () => {
    serveVersion(() => HttpResponse.json({ buildId: NEW_BUILD_ID }))

    const { result } = renderSignal()

    await waitFor(() => expect(result.current).toBe(NEW_BUILD_ID))
  })

  it('returns null while the deployed build is the running one', async () => {
    serveVersion(() => HttpResponse.json({ buildId: __BUILD_ID__ }))

    const { result, client } = renderSignal()

    await settledWith(client, 'success')
    expect(versionRequests.count).toBe(1)
    expect(result.current).toBeNull()
  })

  it('checks again when the tab regains focus under the app query defaults', async () => {
    let deployed = __BUILD_ID__
    serveVersion(() => HttpResponse.json({ buildId: deployed }))
    const client = new QueryClient({ defaultOptions: appQueryClient.getDefaultOptions() })

    const { result } = renderSignal(client)
    await settledWith(client, 'success')
    expect(result.current).toBeNull()

    deployed = NEW_BUILD_ID
    const now = Date.now()
    vi.spyOn(Date, 'now').mockReturnValue(now + STALE_TIME.SHORT + 1)
    act(() => {
      window.dispatchEvent(new Event('visibilitychange'))
    })

    await waitFor(() => expect(result.current).toBe(NEW_BUILD_ID))
    expect(versionRequests.count).toBe(2)
  })

  it('never fetches in development', async () => {
    vi.stubEnv('DEV', true)
    serveVersion(() => HttpResponse.json({ buildId: NEW_BUILD_ID }))

    const { result, client } = renderSignal()

    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))
    expect(versionRequests.count).toBe(0)
    expect(client.getQueryCache().findAll()[0]?.state.fetchStatus).toBe('idle')
    expect(result.current).toBeNull()
  })

  it.each([
    { label: 'a 404', resolver: () => new HttpResponse(null, { status: 404 }) },
    {
      label: 'a 500',
      resolver: () => HttpResponse.json({ buildId: NEW_BUILD_ID }, { status: 500 }),
    },
    { label: 'a network failure', resolver: () => HttpResponse.error() },
    { label: 'a non-JSON body', resolver: () => HttpResponse.text('<!doctype html>') },
    { label: 'an empty build id', resolver: () => HttpResponse.json({ buildId: '' }) },
    { label: 'a body without a build id', resolver: () => HttpResponse.json({ version: 1 }) },
    {
      label: 'a body with an unknown key',
      resolver: () => HttpResponse.json({ buildId: NEW_BUILD_ID, commit: 'abc' }),
    },
  ])('returns null and leaves the server status alone on $label', async ({ resolver }) => {
    serveVersion(resolver)

    const { result, client } = renderSignal()

    await settledWith(client, 'error')
    expect(versionRequests.count).toBe(1)
    expect(result.current).toBeNull()
    expect(useServerStatusStore.getState().status).toBe('ok')
  })

  it('leaves a down server status down when the version file answers', async () => {
    useServerStatusStore.setState({ status: 'down' })
    serveVersion(() => HttpResponse.json({ buildId: NEW_BUILD_ID }))

    const { result } = renderSignal()

    await waitFor(() => expect(result.current).toBe(NEW_BUILD_ID))
    expect(useServerStatusStore.getState().status).toBe('down')
  })
})
