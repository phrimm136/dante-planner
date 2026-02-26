import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import { PlannerDetailHeader } from './PlannerDetailHeader'
import type { SaveablePlanner, MDPlannerContent } from '@/types/PlannerTypes'

// ── Router ────────────────────────────────────────────────────
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

// ── i18n ─────────────────────────────────────────────────────
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'en' },
    }),
  }
})

// ── Toast ─────────────────────────────────────────────────────
vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

// ── API errors ────────────────────────────────────────────────
vi.mock('@/lib/api', () => ({
  BannedError: class BannedError extends Error {},
  TimedOutError: class TimedOutError extends Error {},
}))

// ── Child dialogs ─────────────────────────────────────────────
vi.mock('./ApplyLatestMirrorDialog', () => ({
  ApplyLatestMirrorDialog: ({
    open,
    onConfirm,
  }: {
    open: boolean
    onConfirm: () => void
    onOpenChange: (v: boolean) => void
    isPending: boolean
  }) =>
    open ? (
      <button data-testid="confirm-apply" onClick={onConfirm}>
        Confirm
      </button>
    ) : null,
}))
vi.mock('./CopyUrlButton', () => ({ CopyUrlButton: () => null }))
vi.mock('./DeleteConfirmDialog', () => ({ DeleteConfirmDialog: () => null }))
vi.mock('./ModeratorDeleteDialog', () => ({ ModeratorDeleteDialog: () => null }))
vi.mock('./PublishSyncOffWarningDialog', () => ({ PublishSyncOffWarningDialog: () => null }))

// ── Auth ──────────────────────────────────────────────────────
vi.mock('@/hooks/useAuthQuery', () => ({
  useAuthQuery: () => ({
    data: { role: 'USER', usernameEpithet: 'Test', usernameSuffix: '0000' },
  }),
}))

// ── Storage ───────────────────────────────────────────────────
const mockSavePlanner = vi.fn().mockResolvedValue({ success: true })
vi.mock('@/hooks/usePlannerStorage', () => ({
  usePlannerStorage: () => ({ savePlanner: mockSavePlanner }),
}))

// ── Sync adapter ──────────────────────────────────────────────
const mockSyncToServer = vi.fn()
vi.mock('@/hooks/usePlannerSyncAdapter', () => ({
  usePlannerSyncAdapter: () => ({ syncToServer: mockSyncToServer }),
}))

// ── Config ────────────────────────────────────────────────────
const CURRENT_VERSION = 7
vi.mock('@/hooks/usePlannerConfig', () => ({
  usePlannerConfig: () => ({
    mdCurrentVersion: CURRENT_VERSION,
    schemaVersion: 1,
    mdAvailableVersions: [6, 7],
    rrAvailableVersions: [1],
  }),
}))

// ── Mutation stubs ────────────────────────────────────────────
vi.mock('@/hooks/usePlannerSubscription', () => ({
  usePlannerSubscription: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('@/hooks/usePlannerDelete', () => ({
  usePlannerDelete: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('@/hooks/useModeratorPlannerDelete', () => ({
  useModeratorPlannerDelete: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('@/hooks/usePlannerPublish', () => ({
  usePlannerPublish: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('@/hooks/usePlannerOwnerNotifications', () => ({
  useToggleOwnerNotifications: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('@/hooks/useEGOGiftListData', () => ({
  useEGOGiftListData: () => ({ spec: {}, i18n: {} }),
}))

// ── Query keys ────────────────────────────────────────────────
vi.mock('@/hooks/useSavedPlannerQuery', () => ({
  plannerQueryKeys: {
    detail: (id: string) => ['planner', id] as const,
    list: () => ['planner', 'list'] as const,
  },
}))
vi.mock('@/hooks/usePublishedPlannerQuery', () => ({
  publishedPlannerQueryKeys: {
    detail: (id: string) => ['published-planner', id] as const,
  },
}))

// ── Asset / formatting helpers ────────────────────────────────
vi.mock('@/lib/assetPaths', () => ({
  getKeywordIconPath: (keyword: string) => `/icons/${keyword}.webp`,
}))
vi.mock('@/lib/formatUsername', () => ({
  formatUsername: () => 'TestUser#0000',
}))

// ────────────────────────────────────────────────────────────────
// Test data
// ────────────────────────────────────────────────────────────────

const PLANNER_ID = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'

const EMPTY_CONTENT: MDPlannerContent = {
  selectedKeywords: [],
  selectedBuffIds: [],
  selectedGiftKeyword: null,
  selectedGiftIds: [],
  observationGiftIds: [],
  comprehensiveGiftIds: [],
  equipment: {},
  deploymentOrder: [],
  skillEAState: {},
  floorSelections: [],
  sectionNotes: {},
}

/** Server response returned by syncToServer — carries bumped syncVersion */
const syncedPlanner: SaveablePlanner = {
  metadata: {
    id: PLANNER_ID,
    title: 'Test Plan',
    status: 'saved',
    schemaVersion: 1,
    contentVersion: CURRENT_VERSION,
    plannerType: 'MIRROR_DUNGEON',
    syncVersion: 2,
    createdAt: '2026-01-01T00:00:00Z',
    lastModifiedAt: '2026-01-01T00:00:00Z',
    savedAt: '2026-01-01T00:00:00Z',
    deviceId: 'device-123',
    published: false,
  },
  config: { type: 'MIRROR_DUNGEON', category: '5F' },
  content: EMPTY_CONTENT,
}

function makePlanner(
  overrides: Partial<SaveablePlanner['metadata']> = {}
): SaveablePlanner {
  return {
    metadata: {
      id: PLANNER_ID,
      title: 'Test Plan',
      status: 'saved',
      schemaVersion: 1,
      contentVersion: 6, // behind CURRENT_VERSION — makes button visible
      plannerType: 'MIRROR_DUNGEON',
      syncVersion: 1,
      createdAt: '2026-01-01T00:00:00Z',
      lastModifiedAt: '2026-01-01T00:00:00Z',
      savedAt: '2026-01-01T00:00:00Z',
      deviceId: 'device-123',
      published: false,
      ...overrides,
    },
    config: { type: 'MIRROR_DUNGEON', category: '5F' },
    content: EMPTY_CONTENT,
  }
}

// ────────────────────────────────────────────────────────────────
// Helpers
// ────────────────────────────────────────────────────────────────

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return {
    wrapper: ({ children }: { children: React.ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
    queryClient,
  }
}

/** Click the Apply button, then confirm via the dialog. */
async function triggerApplyLatestMirror() {
  fireEvent.click(screen.getByText('pages.plannerMD.applyLatestMirror.button'))
  await waitFor(() => screen.getByTestId('confirm-apply'))
  fireEvent.click(screen.getByTestId('confirm-apply'))
}

// ────────────────────────────────────────────────────────────────
// Tests
// ────────────────────────────────────────────────────────────────

describe('PlannerDetailHeader – Apply Latest Mirror', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSyncToServer.mockResolvedValue(syncedPlanner)
    mockSavePlanner.mockResolvedValue({ success: true })
  })

  describe('button visibility', () => {
    it('is visible when plan contentVersion is behind current version', () => {
      const { wrapper } = createWrapper()
      render(
        <PlannerDetailHeader
          variant="personal"
          planner={makePlanner({ contentVersion: 6 })}
          isOwner={true}
          isAuthenticated={true}
          syncEnabled={false}
        />,
        { wrapper }
      )

      expect(
        screen.getByText('pages.plannerMD.applyLatestMirror.button')
      ).toBeDefined()
    })

    it('is hidden when plan contentVersion equals current version', () => {
      const { wrapper } = createWrapper()
      render(
        <PlannerDetailHeader
          variant="personal"
          planner={makePlanner({ contentVersion: CURRENT_VERSION })}
          isOwner={true}
          isAuthenticated={true}
          syncEnabled={false}
        />,
        { wrapper }
      )

      expect(
        screen.queryByText('pages.plannerMD.applyLatestMirror.button')
      ).toBeNull()
    })
  })

  describe('personal variant – sync decisions', () => {
    it('saves only locally when sync is off and plan is unpublished', async () => {
      const { wrapper } = createWrapper()
      render(
        <PlannerDetailHeader
          variant="personal"
          planner={makePlanner({ published: false })}
          isOwner={true}
          isAuthenticated={true}
          syncEnabled={false}
        />,
        { wrapper }
      )

      await triggerApplyLatestMirror()

      await waitFor(() => {
        expect(mockSyncToServer).not.toHaveBeenCalled()
        expect(mockSavePlanner).toHaveBeenCalledWith(
          expect.objectContaining({
            metadata: expect.objectContaining({
              contentVersion: CURRENT_VERSION,
              syncVersion: 1, // unchanged — no server interaction
            }),
          })
        )
      })
    })

    it('syncs to server and persists server response when sync is on', async () => {
      const { wrapper } = createWrapper()
      render(
        <PlannerDetailHeader
          variant="personal"
          planner={makePlanner()}
          isOwner={true}
          isAuthenticated={true}
          syncEnabled={true}
        />,
        { wrapper }
      )

      await triggerApplyLatestMirror()

      await waitFor(() => {
        expect(mockSyncToServer).toHaveBeenCalledWith(
          expect.objectContaining({
            metadata: expect.objectContaining({ contentVersion: CURRENT_VERSION }),
          })
        )
        // Server response (with bumped syncVersion) is persisted — not the local draft
        expect(mockSavePlanner).toHaveBeenCalledWith(syncedPlanner)
      })
    })

    it('syncs to server when plan is published even if sync is off', async () => {
      const { wrapper } = createWrapper()
      render(
        <PlannerDetailHeader
          variant="personal"
          planner={makePlanner({ published: true })}
          isOwner={true}
          isAuthenticated={true}
          syncEnabled={false}
        />,
        { wrapper }
      )

      await triggerApplyLatestMirror()

      await waitFor(() => {
        expect(mockSyncToServer).toHaveBeenCalledWith(
          expect.objectContaining({
            metadata: expect.objectContaining({ contentVersion: CURRENT_VERSION }),
          })
        )
        expect(mockSavePlanner).toHaveBeenCalledWith(syncedPlanner)
      })
    })

    it('saves only locally when not authenticated', async () => {
      const { wrapper } = createWrapper()
      render(
        <PlannerDetailHeader
          variant="personal"
          planner={makePlanner()}
          isOwner={true}
          isAuthenticated={false}
          syncEnabled={true}
        />,
        { wrapper }
      )

      await triggerApplyLatestMirror()

      await waitFor(() => {
        expect(mockSyncToServer).not.toHaveBeenCalled()
        expect(mockSavePlanner).toHaveBeenCalledWith(
          expect.objectContaining({
            metadata: expect.objectContaining({ contentVersion: CURRENT_VERSION }),
          })
        )
      })
    })
  })
})
