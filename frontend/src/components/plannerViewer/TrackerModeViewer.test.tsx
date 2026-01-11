import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TrackerModeViewer } from './TrackerModeViewer'
import type { SaveablePlanner, MDPlannerContent } from '@/types/PlannerTypes'

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => {
      if (key === 'pages.plannerMD.floor' && options?.number) {
        return `Floor ${options.number}`
      }
      if (key === 'pages.plannerMD.noteEditor.placeholder') return 'Note placeholder'
      if (key === 'pages.plannerMD.startBuffs') return 'Start Buffs'
      if (key === 'pages.plannerMD.egoGiftObservation') return 'EGO Gift Observation'
      if (key === 'pages.plannerMD.skillReplacement.title') return 'Skill Replacement'
      if (key === 'pages.plannerMD.loading.EGOGiftData') return 'Loading EGO Gift Data...'
      if (key === 'pages.plannerMD.loading.themePackData') return 'Loading theme pack data...'
      return key
    },
  }),
}))

// Mock useTrackerState hook
const mockTrackerState = {
  state: {
    deploymentOrder: ['YiSang', 'Faust', 'DonQuixote'],
    currentSkillCounts: {
      YiSang: { 0: 3, 1: 2, 2: 1 },
      Faust: { 0: 3, 1: 2, 2: 1 },
    },
    doneMarks: {},
    hoveredThemePack: null,
  },
  setDeploymentOrder: vi.fn(),
  updateCurrentSkillCount: vi.fn(),
  toggleDoneMark: vi.fn(),
  setHoveredThemePack: vi.fn(),
  resetState: vi.fn(),
}

vi.mock('@/hooks/useTrackerState', () => ({
  useTrackerState: () => mockTrackerState,
}))

// Mock deserializeSets
vi.mock('@/schemas/PlannerSchemas', () => ({
  deserializeSets: vi.fn((data) => ({
    selectedKeywords: new Set(data.selectedKeywords || []),
    selectedBuffIds: new Set(data.selectedBuffIds || []),
    selectedGiftIds: new Set(data.selectedGiftIds || []),
    observationGiftIds: new Set(data.observationGiftIds || []),
    comprehensiveGiftIds: new Set(data.comprehensiveGiftIds || []),
    floorSelections: (data.floorSelections || []).map((sel: { giftIds: string[] }) => ({
      ...sel,
      giftIds: new Set(sel.giftIds || []),
    })),
  })),
}))

// Mock child components
vi.mock('./DeckTrackerPanel', () => ({
  DeckTrackerPanel: ({ deploymentOrder }: { deploymentOrder: string[] }) => (
    <div data-testid="deck-tracker-panel">Deployment: {deploymentOrder.join(', ')}</div>
  ),
}))

vi.mock('./SkillTrackerPanel', () => ({
  SkillTrackerPanel: () => <div data-testid="skill-tracker-panel">SkillTrackerPanel</div>,
}))

vi.mock('./FloorTrackerSection', () => ({
  FloorTrackerSection: ({ floorNumber, floorIndex }: { floorNumber: number; floorIndex: number }) => (
    <div data-testid={`floor-tracker-section-${floorIndex}`}>Floor {floorNumber}</div>
  ),
}))

vi.mock('@/components/startBuff/StartBuffSection', () => ({
  StartBuffSection: () => <div data-testid="start-buff-section">StartBuffSection</div>,
}))

vi.mock('@/components/startGift/StartGiftSummary', () => ({
  StartGiftSummary: () => <div data-testid="start-gift-summary">StartGiftSummary</div>,
}))

vi.mock('@/components/egoGift/EGOGiftObservationSummary', () => ({
  EGOGiftObservationSummary: () => <div data-testid="ego-gift-observation-summary">EGOGiftObservationSummary</div>,
}))

vi.mock('@/components/egoGift/ComprehensiveGiftSummary', () => ({
  ComprehensiveGiftSummary: () => <div data-testid="comprehensive-gift-summary">ComprehensiveGiftSummary</div>,
}))

vi.mock('@/components/noteEditor/NoteEditor', () => ({
  NoteEditor: ({ disabled }: { disabled?: boolean }) => (
    <div data-testid="note-editor" data-disabled={disabled}>NoteEditor</div>
  ),
}))

vi.mock('@/components/ui/skeleton', () => ({
  Skeleton: ({ className }: { className?: string }) => <div data-testid="skeleton" className={className} />,
}))

describe('TrackerModeViewer', () => {
  const createMockPlanner = (floorCount: number): SaveablePlanner => {
    const floorSelections = Array.from({ length: floorCount }, (_, i) => ({
      themePackId: `themePack${i + 1}`,
      difficulty: i as 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14,
      giftIds: [`gift${i + 1}A`, `gift${i + 1}B`],
    }))

    const sectionNotes = {
      deckBuilder: { content: { type: 'doc' as const, content: [] } },
      startBuffs: { content: { type: 'doc' as const, content: [] } },
      startGifts: { content: { type: 'doc' as const, content: [] } },
      observation: { content: { type: 'doc' as const, content: [] } },
      comprehensiveGifts: { content: { type: 'doc' as const, content: [] } },
      skillReplacement: { content: { type: 'doc' as const, content: [] } },
      ...Object.fromEntries(floorSelections.map((_, i) => [`floor-${i}`, { content: { type: 'doc' as const, content: [] } }])),
    }

    const content: MDPlannerContent = {
      equipment: {
        YiSang: { identity: { id: 'ident1', uptie: 3 }, ego: [] },
        Faust: { identity: { id: 'ident2', uptie: 4 }, ego: [] },
      },
      deploymentOrder: ['YiSang', 'Faust'],
      selectedKeywords: [],
      selectedBuffIds: [1, 2],
      selectedGiftKeyword: null,
      selectedGiftIds: ['gift1', 'gift2'],
      observationGiftIds: ['obsGift1'],
      comprehensiveGiftIds: ['compGift1', 'compGift2'],
      skillEAState: {
        YiSang: { 0: 3, 1: 2, 2: 1 },
        Faust: { 0: 3, 1: 2, 2: 1 },
      },
      floorSelections,
      sectionNotes,
    }

    return {
      metadata: {
        id: 'planner-1',
        status: 'draft',
        schemaVersion: 1,
        contentVersion: 6,
        plannerType: 'MIRROR_DUNGEON',
        syncVersion: 1,
        createdAt: '2025-01-01T00:00:00Z',
        lastModifiedAt: '2025-01-01T00:00:00Z',
        savedAt: null,
        userId: null,
        deviceId: 'device-1',
      },
      config: {
        type: 'MIRROR_DUNGEON',
        category: floorCount === 5 ? '5F' : floorCount === 10 ? '10F' : '15F',
      },
      content,
    }
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockTrackerState.state.deploymentOrder = ['YiSang', 'Faust', 'DonQuixote']
  })

  describe('Section Rendering', () => {
    it('renders all required sections', () => {
      const planner = createMockPlanner(5)
      render(<TrackerModeViewer planner={planner} />)

      expect(screen.getByTestId('deck-tracker-panel')).toBeDefined()
      expect(screen.getByTestId('start-buff-section')).toBeDefined()
      expect(screen.getByTestId('start-gift-summary')).toBeDefined()
      expect(screen.getByTestId('ego-gift-observation-summary')).toBeDefined()
      expect(screen.getByTestId('comprehensive-gift-summary')).toBeDefined()
      expect(screen.getByTestId('skill-tracker-panel')).toBeDefined()
    })

    it('renders single unified floor tracker section', () => {
      const planner = createMockPlanner(5)
      render(<TrackerModeViewer planner={planner} />)

      // Should have one unified FloorTrackerSection instead of floor-by-floor sections
      const floorWrapper = screen.getByTestId('unified-floor-tracker-wrapper')
      expect(floorWrapper).toBeDefined()
    })

    it('renders unified floor tracker for different floor counts', () => {
      const planner = createMockPlanner(10)
      render(<TrackerModeViewer planner={planner} />)

      // Should still have one unified section regardless of floor count
      const floorWrapper = screen.getByTestId('unified-floor-tracker-wrapper')
      expect(floorWrapper).toBeDefined()
    })

    it('renders note editors in disabled state', () => {
      const planner = createMockPlanner(5)
      render(<TrackerModeViewer planner={planner} />)

      const noteEditors = screen.getAllByTestId('note-editor')
      noteEditors.forEach((editor) => {
        expect(editor.getAttribute('data-disabled')).toBe('true')
      })
    })
  })

  describe('Tracker State Integration', () => {
    it('passes deployment order to DeckTrackerPanel', () => {
      const planner = createMockPlanner(5)
      render(<TrackerModeViewer planner={planner} />)

      const deckPanel = screen.getByTestId('deck-tracker-panel')
      expect(deckPanel.textContent).toContain('YiSang, Faust, DonQuixote')
    })

    it('uses tracker deployment order when modified', () => {
      mockTrackerState.state.deploymentOrder = ['Faust', 'YiSang', 'DonQuixote']
      const planner = createMockPlanner(5)
      render(<TrackerModeViewer planner={planner} />)

      const deckPanel = screen.getByTestId('deck-tracker-panel')
      expect(deckPanel.textContent).toContain('Faust, YiSang, DonQuixote')
    })

    it('uses planner deployment order when tracker state is empty', () => {
      mockTrackerState.state.deploymentOrder = []
      const planner = createMockPlanner(5)
      render(<TrackerModeViewer planner={planner} />)

      const deckPanel = screen.getByTestId('deck-tracker-panel')
      expect(deckPanel.textContent).toContain('YiSang, Faust')
    })
  })

  describe('Unified Floor Data', () => {
    it('passes all floor data to unified FloorTrackerSection', () => {
      const planner = createMockPlanner(5)
      render(<TrackerModeViewer planner={planner} />)

      // Unified section should exist and receive all floor data
      const floorWrapper = screen.getByTestId('unified-floor-tracker-wrapper')
      expect(floorWrapper).toBeDefined()
      // All floor selections should be passed to the unified component
    })
  })
})
