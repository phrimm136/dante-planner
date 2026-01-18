import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { PlannerViewer } from './PlannerViewer'
import type { SaveablePlanner, MDPlannerContent } from '@/types/PlannerTypes'

// Mock react-i18next with initReactI18next for proper module loading
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, fallback?: string) => {
        const translations: Record<string, string> = {
          'pages.plannerMD.viewer.guideMode': 'Guide Mode',
          'pages.plannerMD.viewer.trackerMode': 'Tracker Mode',
        }
        return translations[key] ?? fallback ?? key
      },
    }),
  }
})

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
      id: '550e8400-e29b-41d4-a716-446655440000',
      title: 'Test Planner',
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
    })
  })

  describe('Mode Switching', () => {
    it('defaults to guide mode', () => {
      render(<PlannerViewer planner={mockPlanner} />)

      // Guide mode should be visible (not hidden)
      const guideViewer = screen.getByTestId('guide-mode-viewer')
      expect(guideViewer.parentElement).not.toHaveClass('hidden')

      // Tracker should not be mounted initially (lazy mounting)
      expect(screen.queryByTestId('tracker-mode-viewer')).toBeNull()
    })

    it('mounts tracker mode when tracker button clicked', () => {
      render(<PlannerViewer planner={mockPlanner} />)

      const trackerButton = screen.getByText('Tracker Mode')
      fireEvent.click(trackerButton)

      // Both viewers should now exist due to lazy mounting
      expect(screen.getByTestId('tracker-mode-viewer')).toBeDefined()
    })

    it('preserves both viewers after switching back to guide mode', () => {
      render(<PlannerViewer planner={mockPlanner} />)

      // Switch to tracker (mounts tracker)
      fireEvent.click(screen.getByText('Tracker Mode'))
      expect(screen.getByTestId('tracker-mode-viewer')).toBeDefined()

      // Switch back to guide
      fireEvent.click(screen.getByText('Guide Mode'))

      // Both viewers stay mounted (CSS visibility toggle)
      expect(screen.getByTestId('guide-mode-viewer')).toBeDefined()
      expect(screen.getByTestId('tracker-mode-viewer')).toBeDefined()
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

      // Component should still be stable
      expect(screen.getByTestId('tracker-mode-viewer')).toBeDefined()
      expect(screen.getByTestId('guide-mode-viewer')).toBeDefined()
    })
  })
})
