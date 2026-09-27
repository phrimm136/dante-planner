import { describe, it, expect, vi, beforeEach } from 'vitest'
import { act, render, screen } from '@testing-library/react'

import type { PublishedPlannerQueryResult } from '../hooks/usePublishedPlannerQuery'

const lazyState = vi.hoisted(() => {
  const state = {
    loaded: { comment: false, list: false, toolbar: false, pills: false },
    release: () => {},
    gate: Promise.resolve(),
    reset() {
      state.loaded = { comment: false, list: false, toolbar: false, pills: false }
      state.gate = new Promise<void>((resolve) => {
        state.release = resolve
      })
    },
  }
  return state
})

const publishedPlanner = vi.hoisted(
  () =>
    ({
      apiData: { authorUsernameEpithet: 'author', authorUsernameSuffix: '0001' },
      planner: {
        metadata: { id: 'published-planner-1' },
        config: { type: 'MIRROR_DUNGEON', category: '5F' },
      },
    }) as unknown as PublishedPlannerQueryResult,
)

const routerLocation = vi.hoisted(() => ({ hash: '' }))

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
  useParams: () => ({ id: 'published-planner-1' }),
  useNavigate: () => vi.fn<() => void>(),
  useLocation: <T,>(opts?: { select?: (location: { hash: string }) => T }) =>
    opts?.select ? opts.select(routerLocation) : routerLocation,
}))

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key }),
  }
})

vi.mock('../hooks/usePublishedPlannerQuery', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../hooks/usePublishedPlannerQuery')>()
  return { ...actual, usePublishedPlannerQuery: () => publishedPlanner }
})

vi.mock('../hooks/useMDGesellschaftFilters', () => ({
  useMDGesellschaftFilters: () => ({
    filters: { page: 0, mode: 'published' },
    setFilters: vi.fn<() => void>(),
  }),
}))

vi.mock('@/shared/auth/hooks/useAuthQuery', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/auth/hooks/useAuthQuery')>()
  return { ...actual, useAuthQuery: () => ({ data: null }) }
})

vi.mock('@/shared/userSettings/hooks/useUserSettings', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('@/shared/userSettings/hooks/useUserSettings')>()
  return { ...actual, useUserSettingsQuery: () => ({ data: undefined }) }
})

vi.mock('../components/plannerViewer/PlannerViewer', () => ({
  PlannerViewer: () => <div data-testid="planner-viewer">Viewer</div>,
}))

vi.mock('../components/plannerViewer/PublishedPlannerHeader', () => ({
  PublishedPlannerHeader: () => <div data-testid="published-planner-header">Header</div>,
}))

vi.mock('../components/plannerViewer/PlannerDetailFooter', () => ({
  PlannerDetailFooter: () => <div data-testid="planner-detail-footer">Footer</div>,
}))

type ObserverCallback = (entries: Array<{ isIntersecting: boolean }>) => void

let observerCallbacks: ObserverCallback[] = []

class ControlledIntersectionObserver {
  private readonly callback: ObserverCallback
  constructor(callback: ObserverCallback) {
    this.callback = callback
  }
  observe() {
    observerCallbacks.push(this.callback)
  }
  disconnect() {
    observerCallbacks = observerCallbacks.filter((cb) => cb !== this.callback)
  }
  unobserve() {}
  takeRecords() {
    return []
  }
}

function intersectAll() {
  act(() => {
    for (const callback of observerCallbacks) {
      callback([{ isIntersecting: true }])
    }
  })
}

function mockLazySections() {
  vi.doMock('@/shared/comment', async () => {
    lazyState.loaded.comment = true
    await lazyState.gate
    return { CommentSection: () => <div data-testid="comment-section">Comments</div> }
  })

  vi.doMock('../components/plannerList/PublishedPlannerList', async () => {
    lazyState.loaded.list = true
    await lazyState.gate
    return { PublishedPlannerList: () => <div data-testid="published-planner-list">List</div> }
  })

  vi.doMock('../components/plannerList/MDPlannerToolbar', async () => {
    lazyState.loaded.toolbar = true
    await lazyState.gate
    return { MDPlannerToolbar: () => <div data-testid="md-planner-toolbar">Toolbar</div> }
  })

  vi.doMock('../components/plannerList/PlannerListFilterPills', async () => {
    lazyState.loaded.pills = true
    await lazyState.gate
    return { PlannerListFilterPills: () => <div data-testid="planner-filter-pills">Pills</div> }
  })
}

async function renderPage() {
  const { default: PlannerMDGesellschaftDetailPage } =
    await import('../PlannerMDGesellschaftDetailPage')
  render(<PlannerMDGesellschaftDetailPage />)
  await screen.findByTestId('planner-viewer')
}

beforeEach(() => {
  vi.resetModules()
  lazyState.reset()
  mockLazySections()
  observerCallbacks = []
  routerLocation.hash = ''
  globalThis.IntersectionObserver =
    ControlledIntersectionObserver as unknown as typeof IntersectionObserver
})

describe('PlannerMDGesellschaftDetailPage deferred sections', () => {
  it('renders placeholders and imports neither section before the sentinels near the viewport', async () => {
    await renderPage()

    expect(screen.getByTestId('comment-section-placeholder')).toBeDefined()
    expect(screen.getByTestId('planner-list-placeholder')).toBeDefined()
    expect(screen.queryByTestId('comment-section')).toBeNull()
    expect(screen.queryByTestId('published-planner-list')).toBeNull()
    expect(lazyState.loaded).toEqual({ comment: false, list: false, toolbar: false, pills: false })
  })

  it('imports and renders both sections once their sentinels intersect', async () => {
    lazyState.release()
    await renderPage()

    intersectAll()

    expect(await screen.findByTestId('comment-section')).toBeDefined()
    expect(await screen.findByTestId('published-planner-list')).toBeDefined()
    expect(screen.getByTestId('md-planner-toolbar')).toBeDefined()
    expect(screen.getByTestId('planner-filter-pills')).toBeDefined()
    expect(screen.queryByTestId('comment-section-placeholder')).toBeNull()
    expect(screen.queryByTestId('planner-list-placeholder')).toBeNull()
  })

  it('shows the planner content while the section modules are still loading', async () => {
    await renderPage()

    intersectAll()
    await vi.waitFor(() => {
      expect(lazyState.loaded).toEqual({ comment: true, list: true, toolbar: true, pills: true })
    })

    expect(screen.getByTestId('published-planner-header')).toBeDefined()
    expect(screen.getByTestId('planner-viewer')).toBeDefined()
    expect(screen.getByTestId('planner-detail-footer')).toBeDefined()
    expect(screen.getByTestId('comment-section-placeholder')).toBeDefined()
    expect(screen.getByTestId('planner-list-placeholder')).toBeDefined()

    lazyState.release()

    expect(await screen.findByTestId('comment-section')).toBeDefined()
    expect(await screen.findByTestId('published-planner-list')).toBeDefined()
  })
})

describe('PlannerMDGesellschaftDetailPage comment deep link', () => {
  it('renders the comment section at mount when the location targets a comment', async () => {
    routerLocation.hash = 'comment-abc'
    lazyState.release()
    await renderPage()

    expect(await screen.findByTestId('comment-section')).toBeDefined()
    expect(screen.queryByTestId('comment-section-placeholder')).toBeNull()
    expect(screen.getByTestId('planner-list-placeholder')).toBeDefined()
    expect(lazyState.loaded.list).toBe(false)
  })
})
