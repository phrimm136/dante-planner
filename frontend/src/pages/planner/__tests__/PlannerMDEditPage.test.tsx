import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import PlannerMDEditPage from '../PlannerMDEditPage'
import { useSavedPlannerQuery } from '../hooks/useSavedPlannerQuery'
import type { SaveablePlanner, MDPlannerContent } from '../types/PlannerTypes'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
  useParams: () => ({ id: 'test-planner-123' }),
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => fallback || key,
  }),
  initReactI18next: {
    type: '3rdParty',
    init: () => {},
  },
}))

vi.mock('../hooks/useSavedPlannerQuery')

vi.mock('../components/planner/PlannerEditEditor', async (importOriginal) => {
  const { usePlannerEditorStore } = await import('../stores/usePlannerEditorStore')
  return {
    ...(await importOriginal<typeof import('../components/planner/PlannerEditEditor')>()),
    PlannerEditEditor: ({ planner }: { planner?: SaveablePlanner }) => {
      const reset = usePlannerEditorStore((s) => s.reset)
      const level = usePlannerEditorStore((s) => s.equipment['1']?.identity.level)
      return (
        <div data-testid="editor-content">
          EditEditor - Planner ID: {planner?.metadata.id || 'none'}
          <button onClick={reset}>reset deck</button>
          <span data-testid="default-level">{level}</span>
        </div>
      )
    },
  }
})

vi.mock('@/components/feedback/ErrorBoundary', () => ({
  ErrorBoundary: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

const mockPlanner: SaveablePlanner = {
  metadata: {
    id: 'test-planner-123',
    title: 'Test Planner',
    status: 'draft',
    schemaVersion: 2,
    contentVersion: 6,
    plannerType: 'MIRROR_DUNGEON',
    syncVersion: 1,
    createdAt: '2026-01-10T00:00:00Z',
    lastModifiedAt: '2026-01-10T00:00:00Z',
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

describe('PlannerMDEditPage', () => {
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

  describe('data loading', () => {
    it('loads planner via useSavedPlannerQuery hook', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <PlannerMDEditPage />
        </QueryClientProvider>,
      )

      expect(useSavedPlannerQuery).toHaveBeenCalledWith('test-planner-123')
    })

    it('renders the edit editor with the loaded planner', async () => {
      render(
        <QueryClientProvider client={queryClient}>
          <PlannerMDEditPage />
        </QueryClientProvider>,
      )

      const editor = await screen.findByTestId('editor-content')
      expect(editor).toBeDefined()
      expect(editor.textContent).toContain('EditEditor')
      expect(editor.textContent).toContain('Planner ID: test-planner-123')
    })
  })

  describe('identity level cap', () => {
    it("equips a default deck at the MD6 planner's cap of 55", async () => {
      render(
        <QueryClientProvider client={queryClient}>
          <PlannerMDEditPage />
        </QueryClientProvider>,
      )

      fireEvent.click(await screen.findByRole('button', { name: 'reset deck' }))

      expect(screen.getByTestId('default-level').textContent).toBe('55')
    })
  })

  describe('error handling', () => {
    it('shows not found error when planner is null', () => {
      vi.mocked(useSavedPlannerQuery).mockReturnValue(null)

      render(
        <QueryClientProvider client={queryClient}>
          <PlannerMDEditPage />
        </QueryClientProvider>,
      )

      expect(screen.getByText(/Plan Not Found/)).toBeDefined()
      expect(screen.getByText(/Back to/)).toBeDefined()
    })

    it('shows invalid type error when planner type is not MIRROR_DUNGEON', () => {
      const nonMDPlanner: SaveablePlanner = {
        ...mockPlanner,
        config: {
          type: 'REFRACTED_RAILWAY',
          category: 'RR_PLACEHOLDER',
        },
      }
      vi.mocked(useSavedPlannerQuery).mockReturnValue(nonMDPlanner)

      render(
        <QueryClientProvider client={queryClient}>
          <PlannerMDEditPage />
        </QueryClientProvider>,
      )

      expect(screen.getByText(/Invalid Planner Type/)).toBeDefined()
      expect(screen.getByText(/Back to/)).toBeDefined()
    })
  })

  describe('state initialization', () => {
    it('passes loaded planner to the edit editor', async () => {
      render(
        <QueryClientProvider client={queryClient}>
          <PlannerMDEditPage />
        </QueryClientProvider>,
      )

      const editor = await screen.findByTestId('editor-content')
      expect(editor.textContent).toContain('test-planner-123')
    })

    it('routes to the edit editor rather than the create editor', async () => {
      render(
        <QueryClientProvider client={queryClient}>
          <PlannerMDEditPage />
        </QueryClientProvider>,
      )

      const editor = await screen.findByTestId('editor-content')
      expect(editor.textContent).toContain('EditEditor')
    })
  })

  describe('route params', () => {
    it('extracts id from route params', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <PlannerMDEditPage />
        </QueryClientProvider>,
      )

      expect(useSavedPlannerQuery).toHaveBeenCalledWith('test-planner-123')
    })
  })
})
