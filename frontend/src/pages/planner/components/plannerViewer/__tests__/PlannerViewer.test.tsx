import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import type { StoreApi } from 'zustand'
import { PlannerViewer } from '../PlannerViewer'
import { usePlannerStoreApi, useStoreSeason } from '../../../stores/usePlannerStore'
import type { PlannerStore } from '../../../stores/usePlannerStore'
import { useStartBuffListSpec } from '../../../hooks/useStartBuffList'
import { useEGOGiftObservationData } from '@/pages/egoGift'
import type { MDSaveablePlanner, MDPlannerContent } from '../../../types/PlannerTypes'

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

type ViewerMode = 'guide' | 'tracker'

const modeStores = new Map<ViewerMode, StoreApi<PlannerStore>>()

function SeasonProbe({ mode }: { mode: ViewerMode }) {
  modeStores.set(mode, usePlannerStoreApi())
  const { contentVersion } = useStoreSeason()
  return <span data-testid={`season-${mode}`}>{contentVersion}</span>
}

vi.mock('../GuideModeViewer', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../GuideModeViewer')>()
  return {
    GuideModeViewer: ({ planner }: { planner: MDSaveablePlanner }) => (
      <div data-testid="guide-mode-viewer">
        <SeasonProbe mode="guide" />
        <actual.GuideModeViewer planner={planner} />
      </div>
    ),
  }
})

vi.mock('../TrackerModeViewer', () => ({
  TrackerModeViewer: ({ planner }: { planner: MDSaveablePlanner }) => (
    <div data-testid="tracker-mode-viewer">
      Tracker Mode - Planner ID: {planner.metadata.id}
      <SeasonProbe mode="tracker" />
    </div>
  ),
}))

vi.mock('../../deckBuilder/DeckBuilderSummary', () => ({
  DeckBuilderSummary: () => <div data-testid="deck-summary" />,
}))
vi.mock('../../startGift/StartGiftSummary', () => ({
  StartGiftSummary: () => <div data-testid="start-gift" />,
}))
vi.mock('../../skillReplacement/SkillReplacementSection', () => ({
  SkillReplacementSection: () => <div data-testid="skill-replacement" />,
}))
vi.mock('../ComprehensiveGiftGridTracker', () => ({
  ComprehensiveGiftGridTracker: () => <div data-testid="comprehensive" />,
}))
vi.mock('../FloorGalleryTracker', () => ({
  FloorGalleryTracker: () => <div data-testid="floor-gallery" />,
}))
vi.mock('@/components/hooks/useProgressiveReveal', () => ({
  useProgressiveReveal: (count: number) => Array.from({ length: count }, () => true),
}))
vi.mock('../../../hooks/useMirrorDungeonSeason', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../hooks/useMirrorDungeonSeason')>()),
  useIdentityMaxLevel: () => 60,
}))
vi.mock('../../../hooks/useStartBuffList', () => ({
  useStartBuffListSpec: vi.fn(() => ({})),
  useStartBuffListI18n: vi.fn(() => ({})),
}))
vi.mock('@/shared/gameText/hooks/useBattleKeywords', () => ({
  useBattleKeywords: () => ({ data: {} }),
}))
vi.mock('@/pages/egoGift/hooks/useEGOGiftObservationData', () => ({
  useEGOGiftObservationData: vi.fn(() => ({
    data: { observationEgoGiftCostDataList: [], observationEgoGiftDataList: [] },
  })),
}))
vi.mock('@/shared/egoGift/hooks/useEGOGiftListData', () => ({
  useEGOGiftListSpec: () => ({}),
  useEGOGiftListI18n: () => ({}),
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

  const mockPlanner: MDSaveablePlanner = {
    metadata: {
      id: '550e8400-e29b-41d4-a716-446655440000',
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
    content: mockPlannerContent,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    modeStores.clear()
  })

  describe('Season store', () => {
    const season7Planner: MDSaveablePlanner = {
      ...mockPlanner,
      metadata: { ...mockPlanner.metadata, contentVersion: 7 },
    }

    it('feeds the guide sections the saved season through the store', () => {
      render(<PlannerViewer planner={season7Planner} />)

      const buffSeasons = vi.mocked(useStartBuffListSpec).mock.calls.map(([version]) => version)
      const observationSeasons = vi
        .mocked(useEGOGiftObservationData)
        .mock.calls.map(([version]) => version)
      expect(buffSeasons.length).toBeGreaterThan(0)
      expect(new Set(buffSeasons)).toEqual(new Set([7]))
      expect(observationSeasons.length).toBeGreaterThan(0)
      expect(new Set(observationSeasons)).toEqual(new Set([7]))
    })

    it('mounts the tracker into the store the guide reads', () => {
      render(<PlannerViewer planner={season7Planner} />)

      fireEvent.click(screen.getByText('Tracker Mode'))

      expect(screen.getByTestId('season-guide')).toHaveTextContent('7')
      expect(screen.getByTestId('season-tracker')).toHaveTextContent('7')
      expect(modeStores.get('tracker')).toBeDefined()
      expect(modeStores.get('tracker')).toBe(modeStores.get('guide'))
    })
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
