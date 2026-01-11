import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import PlannerMDDetailPage from './PlannerMDDetailPage'
import { useSavedPlannerQuery } from '@/hooks/useSavedPlannerQuery'
import type { SaveablePlanner, MDPlannerContent } from '@/types/PlannerTypes'

// Mock react-router
vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
  useParams: () => ({ id: 'test-planner-123' }),
}))

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => fallback || key,
  }),
}))

// Mock useSavedPlannerQuery
vi.mock('@/hooks/useSavedPlannerQuery')

const mockPlanner: SaveablePlanner = {
  metadata: {
    id: 'test-planner-123',
    status: 'draft',
    schemaVersion: 1,
    contentVersion: 6,
    plannerType: 'MIRROR_DUNGEON',
    syncVersion: 1,
    createdAt: '2026-01-10T00:00:00Z',
    lastModifiedAt: '2026-01-10T00:00:00Z',
    savedAt: null,
    userId: null,
    deviceId: 'device-123',
  },
  config: {
    type: 'MIRROR_DUNGEON',
    category: '5F',
  },
  content: {
    equipment: {},
    deploymentOrder: [],
    selectedBuffIds: [],
    selectedKeywords: [],
    selectedGiftKeyword: null,
    selectedGiftIds: [],
    observationGiftIds: [],
    comprehensiveGiftIds: [],
    skillEAState: {},
    floorSelections: [],
    sectionNotes: {
      deckBuilder: { content: { type: 'doc', content: [] } },
      startBuffs: { content: { type: 'doc', content: [] } },
      startGifts: { content: { type: 'doc', content: [] } },
      observation: { content: { type: 'doc', content: [] } },
      comprehensiveGifts: { content: { type: 'doc', content: [] } },
      skillReplacement: { content: { type: 'doc', content: [] } },
    },
  } as MDPlannerContent,
}

// Mock PlannerViewer
vi.mock('@/components/plannerViewer/PlannerViewer', () => ({
  PlannerViewer: ({ planner }: { planner: SaveablePlanner }) => (
    <div data-testid="planner-viewer">PlannerViewer - ID: {planner.metadata.id}</div>
  ),
}))

// Mock ErrorBoundary
vi.mock('@/components/common/ErrorBoundary', () => ({
  ErrorBoundary: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

describe('PlannerMDDetailPage', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    })
    vi.clearAllMocks()
    vi.mocked(useSavedPlannerQuery).mockReturnValue(mockPlanner)
  })

  it('loads planner via useSavedPlannerQuery hook', () => {
    render(
      <QueryClientProvider client={queryClient}>
        <PlannerMDDetailPage />
      </QueryClientProvider>
    )

    // Verify hook was called with correct ID
    expect(useSavedPlannerQuery).toHaveBeenCalledWith('test-planner-123')
  })

  it('renders PlannerViewer with loaded planner', () => {
    render(
      <QueryClientProvider client={queryClient}>
        <PlannerMDDetailPage />
      </QueryClientProvider>
    )

    const viewer = screen.getByTestId('planner-viewer')
    expect(viewer).toBeDefined()
    expect(viewer.textContent).toContain('test-planner-123')
  })

  it('handles not found planner', () => {
    vi.mocked(useSavedPlannerQuery).mockReturnValue(null)

    render(
      <QueryClientProvider client={queryClient}>
        <PlannerMDDetailPage />
      </QueryClientProvider>
    )

    expect(screen.getByText(/Planner Not Found/)).toBeDefined()
    expect(screen.getByText(/Back to List/)).toBeDefined()
  })

  it('verifies correct hook usage (not direct usePlannerStorage)', () => {
    render(
      <QueryClientProvider client={queryClient}>
        <PlannerMDDetailPage />
      </QueryClientProvider>
    )

    // This test ensures we're using the query hook, not calling usePlannerStorage directly
    expect(useSavedPlannerQuery).toHaveBeenCalled()
  })
})
