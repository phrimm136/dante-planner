import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TrackerModeViewer } from '../TrackerModeViewer'
import { PlannerViewerStore } from '../PlannerViewer'
import {
  createDefaultSkillEAState,
  useStoreDeck,
  useStoreOwnedGiftIds,
  usePlannerStoreApi,
} from '../../../stores/usePlannerStore'
import type { PlannerStore } from '../../../stores/usePlannerStore'
import type { StoreApi } from 'zustand'
import type { SkillEAState } from '../../../types/DeckTypes'
import { DUNGEON_IDX } from '@/shared/gameData'
import type { MDSaveablePlanner, MDPlannerContent } from '../../../types/PlannerTypes'
import { ThemePackIdSchema } from '@/shared/gameData'
import { asEncodedGiftId, asIdentityId } from '@/test-utils/fixtures'

const ENCODED_9001 = asEncodedGiftId('9001')
const ENCODED_9002 = asEncodedGiftId('9002')
const ENCODED_9003 = asEncodedGiftId('9003')
const ENCODED_9004 = asEncodedGiftId('9004')
const ENCODED_9005 = asEncodedGiftId('9005')

const IDENTITY_IDENT1 = asIdentityId('10101')
const IDENTITY_IDENT2 = asIdentityId('10201')

// Create wrapper with QueryClient
function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

// Mock react-i18next
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, options?: Record<string, unknown>) => {
        if (key === 'pages.plannerMD.floor' && options?.number) {
          return `Floor ${options.number as number}`
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
  }
})

// Mock deserializeSets
vi.mock('../../../schemas/PlannerSchemas', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../schemas/PlannerSchemas')>()),
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
vi.mock('../../deckBuilder/DeckBuilderBlock', () => ({
  DeckBuilderBlock: ({ tracker }: { tracker?: { onResetToInitial: () => void } }) => {
    const deck = useStoreDeck()
    const ownedGiftIds = useStoreOwnedGiftIds()
    return (
      <div data-testid="deck-block" data-owned={[...ownedGiftIds].sort().join(',')}>
        Deployment: {deck.deploymentOrder.join(', ')}
        <button type="button" onClick={() => deck.setDeploymentOrder([4, 2])}>
          apply order
        </button>
        <button type="button" onClick={() => deck.setEquipment(() => ({}))}>
          clear equipment
        </button>
        <button type="button" onClick={() => tracker?.onResetToInitial()}>
          reset to initial
        </button>
      </div>
    )
  },
}))

vi.mock('../../../hooks/useMirrorDungeonSeason', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../hooks/useMirrorDungeonSeason')>()),
  useIdentityMaxLevel: () => 60,
}))

vi.mock('../SkillTrackerPanel', () => ({
  SkillTrackerPanel: () => <div data-testid="skill-tracker-panel">SkillTrackerPanel</div>,
}))

vi.mock('../FloorTrackerSection', () => ({
  FloorTrackerSection: ({
    floorNumber,
    floorIndex,
  }: {
    floorNumber: number
    floorIndex: number
  }) => <div data-testid={`floor-tracker-section-${floorIndex}`}>Floor {floorNumber}</div>,
}))

vi.mock('../../startBuff/StartBuffSection', () => ({
  StartBuffSection: () => <div data-testid="start-buff-section">StartBuffSection</div>,
}))

vi.mock('../../startGift/StartGiftSummary', () => ({
  StartGiftSummary: () => <div data-testid="start-gift-summary">StartGiftSummary</div>,
}))

vi.mock('../../egoGift/EGOGiftObservationSummary', () => ({
  EGOGiftObservationSummary: () => (
    <div data-testid="ego-gift-observation-summary">EGOGiftObservationSummary</div>
  ),
}))

vi.mock('../../egoGift/ComprehensiveGiftSummary', () => ({
  ComprehensiveGiftSummary: () => (
    <div data-testid="comprehensive-gift-summary">ComprehensiveGiftSummary</div>
  ),
}))

vi.mock('@/shared/noteEditor/components/ReadOnlyNote', () => ({
  ReadOnlyNote: ({ disabled }: { disabled?: boolean }) => (
    <div data-testid="note-editor" data-disabled={disabled}>
      NoteEditor
    </div>
  ),
}))

vi.mock('@/components/ui/skeleton', () => ({
  Skeleton: ({ className }: { className?: string }) => (
    <div data-testid="skeleton" className={className} />
  ),
}))

// Mock identity and EGO hooks
vi.mock('@/pages/identity/hooks/useIdentityListData', () => ({
  useIdentityListSpec: () => ({}),
}))

vi.mock('@/pages/ego/hooks/useEGOListData', () => ({
  useEGOListSpec: () => ({}),
}))

// Mock progressive reveal (show all sections immediately)
vi.mock('@/components/hooks/useProgressiveReveal', () => ({
  useProgressiveReveal: () => 6,
}))

// Mock other child components
vi.mock('../ComprehensiveGiftGridTracker', () => ({
  ComprehensiveGiftGridTracker: ({ floorCount }: { floorCount: number }) => (
    <div data-testid="comprehensive-gift-grid-tracker" data-floor-count={String(floorCount)}>
      ComprehensiveGiftGridTracker
    </div>
  ),
}))

vi.mock('../HorizontalThemePackGallery', () => ({
  HorizontalThemePackGallery: ({
    floorCount,
    doneMarks,
    onTogglePackDone,
  }: {
    floorCount: number
    doneMarks: Record<number, Set<string>>
    onTogglePackDone: (floorIndex: number, themePackId: string, giftIds: string[]) => void
  }) => (
    <div
      data-testid="horizontal-theme-pack-gallery"
      data-unified="true"
      data-floor-count={String(floorCount)}
      data-done={[...(doneMarks[0] ?? [])].join(',')}
    >
      HorizontalThemePackGallery
      <button type="button" onClick={() => onTogglePackDone(0, '1001', ['9100'])}>
        mark pack done
      </button>
    </div>
  ),
}))

const CHANGED_SINNER_EA: SkillEAState = { 0: 2, 1: 3, 2: 1 }

vi.mock('../../skillReplacement/SkillReplacementSection', () => ({
  SkillReplacementSection: ({
    currentEAState,
    onSinnerSkillEAChange,
  }: {
    currentEAState: Record<string, SkillEAState>
    onSinnerSkillEAChange: (sinnerCode: string, state: SkillEAState) => void
  }) => (
    <div data-testid="skill-replacement-section" data-current={JSON.stringify(currentEAState)}>
      SkillReplacementSection
      <button type="button" onClick={() => onSinnerSkillEAChange('1', CHANGED_SINNER_EA)}>
        exchange skill
      </button>
    </div>
  ),
}))

vi.mock('@/components/layout/PlannerSection', () => ({
  PlannerSection: ({ title, children }: { title: string; children: React.ReactNode }) => (
    <div data-testid="planner-section">
      <h3>{title}</h3>
      {children}
    </div>
  ),
}))

vi.mock('../../SectionNoteDialog', () => ({
  SectionNoteDialog: () => null,
}))

describe('TrackerModeViewer', () => {
  const createMockPlanner = (floorCount: number): MDSaveablePlanner => {
    const floorSelections = Array.from({ length: floorCount }, (_, i) => ({
      themePackId: ThemePackIdSchema.parse(String(1001 + i)),
      difficulty: DUNGEON_IDX.NORMAL,
      giftIds: [asEncodedGiftId(String(9100 + i * 2)), asEncodedGiftId(String(9101 + i * 2))],
    }))

    const sectionNotes = {
      deckBuilder: { content: { type: 'doc' as const, content: [] } },
      startBuffs: { content: { type: 'doc' as const, content: [] } },
      startGifts: { content: { type: 'doc' as const, content: [] } },
      observation: { content: { type: 'doc' as const, content: [] } },
      comprehensiveGifts: { content: { type: 'doc' as const, content: [] } },
      skillReplacement: { content: { type: 'doc' as const, content: [] } },
      ...Object.fromEntries(
        floorSelections.map((_, i) => [
          `floor-${i}`,
          { content: { type: 'doc' as const, content: [] } },
        ]),
      ),
    }

    const content: MDPlannerContent = {
      equipment: {
        YiSang: { identity: { id: IDENTITY_IDENT1, uptie: 3, level: 60 }, egos: {} },
        Faust: { identity: { id: IDENTITY_IDENT2, uptie: 4, level: 60 }, egos: {} },
      },
      deploymentOrder: [0, 1],
      selectedKeywords: [],
      selectedBuffIds: [1, 2],
      selectedGiftKeyword: null,
      selectedGiftIds: [ENCODED_9001, ENCODED_9002],
      observationGiftIds: [ENCODED_9003],
      comprehensiveGiftIds: [ENCODED_9004, ENCODED_9005],
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
        title: 'Tracker Fixture',
        status: 'draft',
        schemaVersion: 2,
        contentVersion: 6,
        plannerType: 'MIRROR_DUNGEON',
        syncVersion: 1,
        createdAt: '2025-01-01T00:00:00Z',
        lastModifiedAt: '2025-01-01T00:00:00Z',
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
  })

  function StoreProbe({ onStore }: { onStore: (store: StoreApi<PlannerStore>) => void }) {
    onStore(usePlannerStoreApi())
    return null
  }

  function renderTracker(planner: MDSaveablePlanner) {
    let store: StoreApi<PlannerStore> | undefined
    const result = render(
      <PlannerViewerStore planner={planner}>
        <TrackerModeViewer planner={planner} />
        <StoreProbe
          onStore={(api) => {
            store = api
          }}
        />
      </PlannerViewerStore>,
      { wrapper: createWrapper() },
    )
    const getStore = () => {
      if (store === undefined) throw new Error('the tracker store never mounted')
      return store
    }
    return { ...result, getStore }
  }

  describe('Section Rendering', () => {
    it('renders all required sections', () => {
      const planner = createMockPlanner(5)
      renderTracker(planner)

      expect(screen.getByTestId('deck-block')).toBeDefined()
      expect(screen.getByTestId('start-buff-section')).toBeDefined()
      expect(screen.getByTestId('start-gift-summary')).toBeDefined()
      expect(screen.getByTestId('ego-gift-observation-summary')).toBeDefined()
      expect(screen.getByTestId('comprehensive-gift-grid-tracker')).toBeDefined()
    })

    it('renders unified floor tracker with HorizontalThemePackGallery', () => {
      const planner = createMockPlanner(5)
      renderTracker(planner)

      // Should have HorizontalThemePackGallery for floor/theme pack management
      const gallery = screen.getByTestId('horizontal-theme-pack-gallery')
      expect(gallery).toBeDefined()
    })

    it('renders unified floor tracker for different floor counts', () => {
      const planner = createMockPlanner(10)
      renderTracker(planner)

      // Should still have gallery regardless of floor count
      const gallery = screen.getByTestId('horizontal-theme-pack-gallery')
      expect(gallery).toBeDefined()
    })
  })

  describe('Tracker State Integration', () => {
    it("seeds the deck from the planner's deployment order", () => {
      renderTracker(createMockPlanner(5))

      expect(screen.getByTestId('deck-block').textContent).toContain('Deployment: 0, 1')
    })

    it('starts the current skill counts at the defaults, not the plan', () => {
      const { getStore } = renderTracker(createMockPlanner(5))

      expect(getStore().getState().skillEAState).toEqual(createDefaultSkillEAState())
      expect(
        JSON.parse(screen.getByTestId('skill-replacement-section').dataset.current ?? ''),
      ).toEqual(createDefaultSkillEAState())
    })

    it('reads and writes the deck and the skill counts through the store', async () => {
      const user = userEvent.setup()
      const { getStore } = renderTracker(createMockPlanner(5))

      await user.click(screen.getByRole('button', { name: 'apply order' }))
      await user.click(screen.getByRole('button', { name: 'exchange skill' }))

      expect(getStore().getState().deploymentOrder).toEqual([4, 2])
      expect(screen.getByTestId('deck-block').textContent).toContain('Deployment: 4, 2')
      const changed = { ...createDefaultSkillEAState(), '1': CHANGED_SINNER_EA }
      expect(getStore().getState().skillEAState).toEqual(changed)
      expect(
        JSON.parse(screen.getByTestId('skill-replacement-section').dataset.current ?? ''),
      ).toEqual(changed)
    })

    it('reset to initial restores the deck and keeps the skill counts and done marks', async () => {
      const user = userEvent.setup()
      const planner = createMockPlanner(5)
      const { getStore } = renderTracker(planner)

      await user.click(screen.getByRole('button', { name: 'apply order' }))
      await user.click(screen.getByRole('button', { name: 'clear equipment' }))
      await user.click(screen.getByRole('button', { name: 'exchange skill' }))
      await user.click(screen.getByRole('button', { name: 'mark pack done' }))
      await user.click(screen.getByRole('button', { name: 'reset to initial' }))

      const state = getStore().getState()
      expect(state.equipment).toEqual(planner.content.equipment)
      expect(state.deploymentOrder).toEqual(planner.content.deploymentOrder)
      expect(state.skillEAState).toEqual({ ...createDefaultSkillEAState(), '1': CHANGED_SINNER_EA })
      expect(screen.getByTestId('horizontal-theme-pack-gallery').dataset.done).toBe('1001')
    })
  })

  describe('Floors past the category count', () => {
    const fifteenFloorsAs5F = (): MDSaveablePlanner => {
      const planner = createMockPlanner(15)
      return { ...planner, config: { ...planner.config, category: '5F' } }
    }

    it('bounds the gallery and the gift grid by the 5F floor count', () => {
      renderTracker(fifteenFloorsAs5F())

      expect(screen.getByTestId('horizontal-theme-pack-gallery').dataset.floorCount).toBe('5')
      expect(screen.getByTestId('comprehensive-gift-grid-tracker').dataset.floorCount).toBe('5')
    })

    it('grants the deck only the gifts of the five floors', () => {
      renderTracker(fifteenFloorsAs5F())

      const fiveFloorGifts = Array.from({ length: 10 }, (_, i) => String(9100 + i))
      const nonFloorGifts = ['9001', '9002', '9003', '9004', '9005']
      expect(screen.getByTestId('deck-block').dataset.owned).toBe(
        [...nonFloorGifts, ...fiveFloorGifts].sort().join(','),
      )
    })
  })

  describe('Unified Floor Data', () => {
    it('passes all floor data to unified HorizontalThemePackGallery', () => {
      const planner = createMockPlanner(5)
      renderTracker(planner)

      // Unified section should exist
      const gallery = screen.getByTestId('horizontal-theme-pack-gallery')
      expect(gallery).toBeDefined()
    })
  })
})
