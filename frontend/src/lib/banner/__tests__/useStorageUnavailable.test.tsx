import { beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import {
  Outlet,
  RouterProvider,
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
} from '@tanstack/react-router'

import { createTestQueryClient } from '@/test-utils/queryClient'

import { useStorageUnavailable } from '../useStorageUnavailable'

const mockProbeStorage = vi.hoisted(() => vi.fn<() => Promise<boolean>>())

vi.mock('@/lib/storage', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/storage')>()
  return { ...actual, probeStorage: () => mockProbeStorage() }
})

function Signal() {
  return <output data-testid="signal">{String(useStorageUnavailable())}</output>
}

function mountAt(path: string) {
  const rootRoute = createRootRoute({
    component: () => (
      <>
        <Signal />
        <Outlet />
      </>
    ),
  })
  const page = (routePath: string) =>
    createRoute({
      getParentRoute: () => rootRoute,
      path: routePath,
      component: () => <div>{routePath}</div>,
    })
  const router = createRouter({
    routeTree: rootRoute.addChildren([page('/identity'), page('/planner/md')]),
    history: createMemoryHistory({ initialEntries: [path] }),
    defaultPendingMs: 0,
  })
  render(
    <QueryClientProvider client={createTestQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

beforeEach(() => {
  mockProbeStorage.mockReset()
})

describe('useStorageUnavailable', () => {
  it('never probes storage off the planner', async () => {
    mockProbeStorage.mockResolvedValue(false)
    mountAt('/identity')

    expect(await screen.findByText('/identity')).toBeInTheDocument()
    expect(screen.getByTestId('signal')).toHaveTextContent('false')
    expect(mockProbeStorage).not.toHaveBeenCalled()
  })

  it('probes once on the planner and reports storage that cannot open', async () => {
    mockProbeStorage.mockResolvedValue(false)
    mountAt('/planner/md')

    expect(await screen.findByText('/planner/md')).toBeInTheDocument()
    expect(await screen.findByText('true')).toBeInTheDocument()
    expect(mockProbeStorage).toHaveBeenCalledTimes(1)
  })

  it('reports nothing on the planner when storage opens', async () => {
    mockProbeStorage.mockResolvedValue(true)
    mountAt('/planner/md')

    expect(await screen.findByText('/planner/md')).toBeInTheDocument()
    await vi.waitFor(() => expect(mockProbeStorage).toHaveBeenCalledTimes(1))
    expect(screen.getByTestId('signal')).toHaveTextContent('false')
  })

  it('stops reporting once the user leaves the planner', async () => {
    mockProbeStorage.mockResolvedValue(false)
    const router = mountAt('/planner/md')
    expect(await screen.findByText('true')).toBeInTheDocument()

    await act(() => router.navigate({ to: '/identity' }))

    expect(await screen.findByText('/identity')).toBeInTheDocument()
    expect(screen.getByTestId('signal')).toHaveTextContent('false')
    expect(mockProbeStorage).toHaveBeenCalledTimes(1)
  })
})
