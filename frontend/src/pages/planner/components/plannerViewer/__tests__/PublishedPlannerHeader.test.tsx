import { Suspense, use } from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import { PublishedPlannerHeader } from '../PublishedPlannerHeader'
import type { PublishedPlannerDetail } from '../../../types/PlannerListTypes'
import type { SaveablePlanner } from '../../../types/PlannerTypes'

vi.mock('@tanstack/react-router', () => ({ useNavigate: () => vi.fn() }))

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

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

const mockShowAppError = vi.fn()
vi.mock('@/lib/errorPresentation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/errorPresentation')>()),
  showAppError: (error: unknown) => mockShowAppError(error),
}))

vi.mock('../ApplyLatestMirrorDialog', () => ({
  ApplyLatestMirrorDialog: ({ open, onConfirm }: { open: boolean; onConfirm: () => void }) =>
    open ? (
      <button data-testid="confirm-apply" onClick={onConfirm}>
        Confirm
      </button>
    ) : null,
}))
vi.mock('../CopyUrlButton', () => ({ CopyUrlButton: () => null }))
vi.mock('../DeleteConfirmDialog', () => ({ DeleteConfirmDialog: () => null }))
vi.mock('../ModeratorDeleteDialog', () => ({ ModeratorDeleteDialog: () => null }))

vi.mock('@/shared/auth/hooks/useAuthQuery', () => ({
  useAuthQuery: () => ({ data: { role: 'USER' } }),
}))

const pending = new Promise<never>(() => {})
const useSuspendForever = (): never => use(pending)
vi.mock('@/shared/egoGift/hooks/useEGOGiftListData', () => ({
  useEGOGiftListSpec: () => useSuspendForever(),
  useEGOGiftListI18n: () => useSuspendForever(),
}))
vi.mock('../../../hooks/usePlannerIdRegistry', () => ({
  usePlannerIdRegistry: () => useSuspendForever(),
}))

const mockLoadValidationInputs = vi.fn()
vi.mock('../../../hooks/loadValidationInputs', () => ({
  loadValidationInputs: (contentVersion: number) => mockLoadValidationInputs(contentVersion),
}))

const mockSaveToLocal = vi.fn()
vi.mock('../../../hooks/usePlannerStorage', () => ({
  usePlannerStorage: () => ({
    saveToLocal: mockSaveToLocal,
    deleteFromLocal: vi.fn(),
    loadFromLocal: vi.fn(),
    writeTombstone: vi.fn(),
  }),
}))

const mockSyncToServer = vi.fn()
vi.mock('../../../hooks/usePlannerSyncAdapter', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../hooks/usePlannerSyncAdapter')>()),
  usePlannerSyncAdapter: () => ({ syncToServer: mockSyncToServer }),
}))

const CURRENT_VERSION = 7
vi.mock('../../../hooks/usePlannerConfig', () => ({
  usePlannerConfig: () => ({ mdCurrentVersion: CURRENT_VERSION, schemaVersion: 2 }),
}))
vi.mock('../../../hooks/usePlannerSubscription', () => ({
  usePlannerSubscription: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('../../../hooks/usePlannerDelete', () => ({
  usePlannerDelete: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('../../../hooks/useModeratorPlannerDelete', () => ({
  useModeratorPlannerDelete: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('../../../hooks/usePlannerOwnerNotifications', () => ({
  useToggleOwnerNotifications: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('@/shared/assets', () => ({
  getKeywordIconPath: (keyword: string) => `/icons/${keyword}.webp`,
}))
vi.mock('@/lib/formatUsername', () => ({ formatUsername: () => 'TestUser#0000' }))

const PLANNER_ID = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'

const publishedFields: Partial<PublishedPlannerDetail> = {
  id: PLANNER_ID,
  title: 'Community Plan',
  category: '5F',
  selectedKeywords: [],
  authorUsernameEpithet: 'Test',
  authorUsernameSuffix: '0000',
  createdAt: '2026-01-01T10:30:00Z',
  viewCount: 12,
  upvotes: 3,
  commentCount: 4,
  isSubscribed: false,
  ownerNotificationsEnabled: false,
}
const published = publishedFields as PublishedPlannerDetail

const saved = {
  metadata: {
    id: PLANNER_ID,
    title: '',
    status: 'saved',
    schemaVersion: 2,
    contentVersion: 6,
    plannerType: 'MIRROR_DUNGEON',
    syncVersion: 1,
    createdAt: '2026-01-01T00:00:00Z',
    lastModifiedAt: '2026-01-01T10:30:00Z',
    published: true,
  },
  config: { type: 'MIRROR_DUNGEON', category: '5F' },
  content: {
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
  },
} as SaveablePlanner

function renderHeader() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={<div data-testid="suspended" />}>
        <PublishedPlannerHeader
          planner={published}
          isOwner
          isAuthenticated
          syncEnabled
          savedPlannerData={saved}
        />
      </Suspense>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('PublishedPlannerHeader', () => {
  it('renders while the game-data queries are unresolved', () => {
    renderHeader()

    expect(screen.queryByTestId('suspended')).toBeNull()
    expect(screen.getByText('Community Plan')).toBeDefined()
  })

  it('shows the validation error and does not sync when apply-latest-mirror meets an invalid planner', async () => {
    mockLoadValidationInputs.mockResolvedValue({
      egoGiftSpec: {},
      egoGiftI18n: {},
      registry: undefined,
    })
    renderHeader()

    fireEvent.click(screen.getByText('pages.plannerMD.applyLatestMirror.button'))
    fireEvent.click(await screen.findByTestId('confirm-apply'))

    await waitFor(() => {
      expect(mockShowAppError).toHaveBeenCalledWith({
        kind: 'validation',
        key: 'planner:pages.plannerMD.publish.missingTitle',
      })
    })
    expect(mockLoadValidationInputs).toHaveBeenCalledWith(CURRENT_VERSION)
    expect(mockSyncToServer).not.toHaveBeenCalled()
    expect(mockSaveToLocal).not.toHaveBeenCalled()
  })
})
