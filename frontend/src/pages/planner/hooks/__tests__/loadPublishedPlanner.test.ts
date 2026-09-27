import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
} from '@tanstack/react-router'

import { NotFoundError } from '@/lib/apiErrors'
import { deferred } from './deferred'
import { publishedPayload } from './publishedPlannerPayload'

const apiMocks = vi.hoisted(() => ({
  get: vi.fn(),
  respond: (): Promise<unknown> => Promise.reject(new Error('no response configured')),
}))

vi.mock('@/lib/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/api')>()),
  ApiClient: {
    get: (endpoint: string, options?: RequestInit) => {
      apiMocks.get(endpoint, options)
      return apiMocks.respond()
    },
  },
}))

import { loadPublishedPlanner } from '@/lib/routeLoaders'
import { queryClient } from '@/lib/queryClient'
import { fetchPublishedPlanner } from '../usePublishedPlannerQuery'
import { untitledPlannerTitle } from '../../lib/loadPlannerTitle'
import { publishedPlannerQueryKeys } from '../../lib/publishedPlannerQueryKeys'

const detailKey = publishedPlannerQueryKeys.detail('p1')

function load() {
  return loadPublishedPlanner({ params: { id: 'p1' }, abortController: new AbortController() })
}

beforeEach(() => {
  queryClient.clear()
  apiMocks.get.mockReset()
  apiMocks.respond = () => Promise.resolve(publishedPayload)
})

describe('loadPublishedPlanner', () => {
  it('caches the validated planner under the detail key and returns its title', async () => {
    const loaded = await load()

    expect(loaded).toEqual({ title: 'Published run' })
    expect(queryClient.getQueryData(detailKey)).toEqual(await fetchPublishedPlanner('p1'))
  })

  it('turns a 404 into the removed state and the untitled title', async () => {
    apiMocks.respond = () => Promise.reject(new NotFoundError('gone'))

    const loaded = await load()

    expect(loaded).toEqual({ title: untitledPlannerTitle() })
    expect(queryClient.getQueryData(detailKey)).toEqual({ removed: true })
  })

  it('makes exactly one network call on a cold run', async () => {
    await load()

    expect(apiMocks.get).toHaveBeenCalledTimes(1)
  })

  it('makes no network call when the cache holds a fresh planner', async () => {
    await load()
    apiMocks.get.mockClear()

    const loaded = await load()

    expect(loaded).toEqual({ title: 'Published run' })
    expect(apiMocks.get).not.toHaveBeenCalled()
  })

  it('joins a fetch already in flight instead of starting its own', async () => {
    const response = deferred<unknown>()
    apiMocks.respond = () => response.promise
    const inFlight = queryClient.prefetchQuery({
      queryKey: detailKey,
      queryFn: ({ signal }) => fetchPublishedPlanner('p1', signal),
    })

    const result = load()
    response.resolve(publishedPayload)

    await expect(result).resolves.toEqual({ title: 'Published run' })
    await inFlight
    expect(apiMocks.get).toHaveBeenCalledTimes(1)
  })

  it('swallows the early request failing after another fetch took over the key', async () => {
    const early = deferred<unknown>()
    const responses = [early.promise, Promise.resolve(publishedPayload)]
    apiMocks.respond = () => responses.shift()!

    const result = load()
    const takeover = queryClient.prefetchQuery({
      queryKey: detailKey,
      queryFn: ({ signal }) => fetchPublishedPlanner('p1', signal),
    })
    early.reject(new Error('early request failed'))

    await expect(result).resolves.toEqual({ title: 'Published run' })
    await takeover
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(apiMocks.get).toHaveBeenCalledTimes(2)
  })
})

describe('loadPublishedPlanner under the router', () => {
  it('makes no network call when router.invalidate re-runs it over a fresh cache', async () => {
    let loaderRuns = 0
    const rootRoute = createRootRoute()
    const detailRoute = createRoute({
      getParentRoute: () => rootRoute,
      path: '/planner/md/gesellschaft/$id',
      loader: (context) => {
        loaderRuns += 1
        return loadPublishedPlanner(context)
      },
    })
    const router = createRouter({
      routeTree: rootRoute.addChildren([detailRoute]),
      history: createMemoryHistory({ initialEntries: ['/planner/md/gesellschaft/p1'] }),
    })

    await router.load()
    expect(apiMocks.get).toHaveBeenCalledTimes(1)
    apiMocks.get.mockClear()

    await router.invalidate()

    expect(loaderRuns).toBe(2)
    expect(apiMocks.get).not.toHaveBeenCalled()
  })
})
