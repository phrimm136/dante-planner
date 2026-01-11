import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { PlannerViewer } from './PlannerViewer'
import type { SaveablePlanner, MDPlannerContent } from '@/types/PlannerTypes'

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => {
      if (key === 'planner.viewer.guideMode') return 'Guide Mode'
      if (key === 'planner.viewer.trackerMode') return 'Tracker Mode'
      return fallback || key
    },
  }),
}))

// Mock GuideModeViewer
vi.mock('./GuideModeViewer', () => ({
  GuideModeViewer: ({ planner }: { planner: SaveablePlanner }) => (
    <div data-testid="guide-mode-viewer">Guide Mode - Planner ID: {planner.metadata.id}</div>
  ),
}))

// Mock TrackerModeViewer
vi.mock('./TrackerModeViewer', () => ({
  TrackerModeViewer: ({ planner }: { planner: SaveablePlanner }) => (
    <div data-testid="tracker-mode-viewer">Tracker Mode - Planner ID: {planner.metadata.id}</div>
  ),
}))

describe('PlannerViewer', () => {
  const mockPlannerContent: MDPlannerContent = {
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
  }

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
    content: mockPlannerContent,
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Planner Loading', () => {
    it('renders with planner data', () => {
      render(<PlannerViewer planner={mockPlanner} />)

      expect(screen.getByText('Guide Mode')).toBeDefined()
      expect(screen.getByText('Tracker Mode')).toBeDefined()
    })

    it('passes planner prop to GuideModeViewer', () => {
      render(<PlannerViewer planner={mockPlanner} />)

      expect(screen.getByTestId('guide-mode-viewer')).toBeDefined()
      expect(screen.getByText(/Planner ID: test-planner-123/)).toBeDefined()
    })
  })

  describe('Mode Switching', () => {
    it('defaults to guide mode', () => {
      render(<PlannerViewer planner={mockPlanner} />)

      expect(screen.getByTestId('guide-mode-viewer')).toBeDefined()
      expect(screen.queryByTestId('tracker-mode-viewer')).toBeNull()
    })

    it('switches to tracker mode when tracker button clicked', () => {
      render(<PlannerViewer planner={mockPlanner} />)

      const trackerButton = screen.getByText('Tracker Mode')
      fireEvent.click(trackerButton)

      expect(screen.getByTestId('tracker-mode-viewer')).toBeDefined()
      expect(screen.queryByTestId('guide-mode-viewer')).toBeNull()
    })

    it('switches back to guide mode when guide button clicked', () => {
      render(<PlannerViewer planner={mockPlanner} />)

      // Switch to tracker
      const trackerButton = screen.getByText('Tracker Mode')
      fireEvent.click(trackerButton)

      expect(screen.getByTestId('tracker-mode-viewer')).toBeDefined()

      // Switch back to guide
      const guideButton = screen.getByText('Guide Mode')
      fireEvent.click(guideButton)

      expect(screen.getByTestId('guide-mode-viewer')).toBeDefined()
      expect(screen.queryByTestId('tracker-mode-viewer')).toBeNull()
    })

    it('passes planner prop to TrackerModeViewer after switch', () => {
      render(<PlannerViewer planner={mockPlanner} />)

      const trackerButton = screen.getByText('Tracker Mode')
      fireEvent.click(trackerButton)

      expect(screen.getByTestId('tracker-mode-viewer')).toBeDefined()
      expect(screen.getByText(/Planner ID: test-planner-123/)).toBeDefined()
    })
  })

  describe('State Preservation', () => {
    it('handles rapid mode switching without errors', () => {
      render(<PlannerViewer planner={mockPlanner} />)

      const trackerButton = screen.getByText('Tracker Mode')
      const guideButton = screen.getByText('Guide Mode')

      // Rapid switching
      fireEvent.click(trackerButton)
      fireEvent.click(guideButton)
      fireEvent.click(trackerButton)
      fireEvent.click(guideButton)
      fireEvent.click(trackerButton)

      expect(screen.getByTestId('tracker-mode-viewer')).toBeDefined()
    })

    it('maintains planner data across mode switches', () => {
      render(<PlannerViewer planner={mockPlanner} />)

      const trackerButton = screen.getByText('Tracker Mode')
      fireEvent.click(trackerButton)

      expect(screen.getByText(/Planner ID: test-planner-123/)).toBeDefined()

      const guideButton = screen.getByText('Guide Mode')
      fireEvent.click(guideButton)

      expect(screen.getByText(/Planner ID: test-planner-123/)).toBeDefined()
    })
  })
})
