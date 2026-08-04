/**
 * DOM snapshots of the two planner detail headers, one row per prop
 * combination that changes what they render.
 *
 * The committed snapshots were produced by an innerHTML comparison against the
 * single pre-split component, so they record its markup exactly.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import { PublishedPlannerHeader } from '../PublishedPlannerHeader'
import { PersonalPlannerHeader } from '../PersonalPlannerHeader'

import type { PublishedPlannerDetail } from '../../../types/PlannerListTypes'
import type { SaveablePlanner, MDPlannerContent } from '../../../types/PlannerTypes'

vi.mock('@tanstack/react-router', () => ({ useNavigate: () => vi.fn() }))

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'EN' },
    }),
  }
})

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

vi.mock('../ApplyLatestMirrorDialog', () => ({ ApplyLatestMirrorDialog: () => null }))
vi.mock('../CopyUrlButton', () => ({ CopyUrlButton: () => null }))
vi.mock('../DeleteConfirmDialog', () => ({ DeleteConfirmDialog: () => null }))
vi.mock('../ModeratorDeleteDialog', () => ({ ModeratorDeleteDialog: () => null }))
vi.mock('../../SyncOffWarningDialog', () => ({ SyncOffWarningDialog: () => null }))

const mockRole = { current: 'NORMAL' as string }
vi.mock('@/shared/auth/hooks/useAuthQuery', () => ({
  useAuthQuery: () => ({ data: { role: mockRole.current } }),
}))

vi.mock('../../../hooks/usePlannerStorage', () => ({
  usePlannerStorage: () => ({ saveToLocal: vi.fn(), deleteFromLocal: vi.fn() }),
}))
vi.mock('../../../hooks/usePlannerSyncAdapter', () => ({
  usePlannerSyncAdapter: () => ({ syncToServer: vi.fn() }),
}))
vi.mock('../../../hooks/usePlannerConfig', () => ({
  usePlannerConfig: () => ({ mdCurrentVersion: 7, schemaVersion: 2 }),
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
vi.mock('../../../hooks/usePlannerPublish', () => ({
  usePlannerPublish: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('../../../hooks/usePlannerOwnerNotifications', () => ({
  useToggleOwnerNotifications: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('@/pages/egoGift/hooks/useEGOGiftListData', () => ({
  useEGOGiftListData: () => ({ spec: {}, i18n: {} }),
}))
vi.mock('@/shared/assets', () => ({
  getKeywordIconPath: (keyword: string) => `/icons/${keyword}.webp`,
}))
vi.mock('@/lib/formatUsername', () => ({ formatUsername: () => 'TestUser#0000' }))

const PLANNER_ID = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'

function emptyContent(selectedKeywords: string[] = []): MDPlannerContent {
  return {
    selectedKeywords,
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
}

function publishedPlanner(overrides: Partial<PublishedPlannerDetail> = {}): PublishedPlannerDetail {
  return {
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
    ...overrides,
  } as PublishedPlannerDetail
}

function savedPlanner(
  metadata: Partial<SaveablePlanner['metadata']> = {},
  content: MDPlannerContent = emptyContent(),
  category = '5F',
): SaveablePlanner {
  return {
    metadata: {
      id: PLANNER_ID,
      title: 'My Plan',
      status: 'saved',
      schemaVersion: 2,
      contentVersion: 6,
      plannerType: 'MIRROR_DUNGEON',
      syncVersion: 1,
      createdAt: '2026-01-01T00:00:00Z',
      lastModifiedAt: '2026-01-01T10:30:00Z',
      savedAt: '2026-01-01T10:30:00Z',
      deviceId: 'device-123',
      published: false,
      ...metadata,
    },
    config: { type: 'MIRROR_DUNGEON', category },
    content,
  } as SaveablePlanner
}

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

interface PublishedRow {
  planner: PublishedPlannerDetail
  isOwner: boolean
  isAuthenticated: boolean
  syncEnabled?: boolean | null
  savedPlannerData?: SaveablePlanner
  onEdit?: () => void
  onCommentClick?: () => void
  role?: string
}

const PUBLISHED_MATRIX: Array<[string, PublishedRow]> = [
  ['anonymous visitor', { planner: publishedPlanner(), isOwner: false, isAuthenticated: false }],
  [
    'authenticated non-owner, not subscribed',
    { planner: publishedPlanner(), isOwner: false, isAuthenticated: true },
  ],
  [
    'authenticated non-owner, subscribed',
    { planner: publishedPlanner({ isSubscribed: true }), isOwner: false, isAuthenticated: true },
  ],
  [
    'moderator viewing someone else',
    { planner: publishedPlanner(), isOwner: false, isAuthenticated: true, role: 'MODERATOR' },
  ],
  [
    'owner with edit and a stale local copy',
    {
      planner: publishedPlanner({ ownerNotificationsEnabled: true }),
      isOwner: true,
      isAuthenticated: true,
      savedPlannerData: savedPlanner({ contentVersion: 6 }),
      onEdit: () => {},
    },
  ],
  [
    'owner whose local copy is already current',
    {
      planner: publishedPlanner(),
      isOwner: true,
      isAuthenticated: true,
      savedPlannerData: savedPlanner({ contentVersion: 7 }),
      onEdit: () => {},
    },
  ],
  [
    'recommended plan with keywords and comment link',
    {
      planner: publishedPlanner({ upvotes: 999, selectedKeywords: ['Burn', 'Bleed'] }),
      isOwner: false,
      isAuthenticated: true,
      onCommentClick: () => {},
    },
  ],
  [
    'untitled plan in an unknown category',
    {
      planner: publishedPlanner({ title: '', category: 'UNKNOWN' as never }),
      isOwner: false,
      isAuthenticated: false,
    },
  ],
]

interface PersonalRow {
  planner: SaveablePlanner
  isAuthenticated: boolean
  syncEnabled?: boolean | null
  onEdit?: () => void
}

const PERSONAL_MATRIX: Array<[string, PersonalRow]> = [
  ['guest, saved', { planner: savedPlanner(), isAuthenticated: false }],
  [
    'guest, draft',
    { planner: savedPlanner({ status: 'draft', savedAt: null }), isAuthenticated: false },
  ],
  [
    'authenticated, sync off, saved',
    { planner: savedPlanner(), isAuthenticated: true, syncEnabled: false },
  ],
  [
    'authenticated, sync on, synced',
    { planner: savedPlanner(), isAuthenticated: true, syncEnabled: true, onEdit: () => {} },
  ],
  [
    'authenticated, sync on, unsynced',
    {
      planner: savedPlanner({ status: 'draft' }),
      isAuthenticated: true,
      syncEnabled: true,
    },
  ],
  [
    'authenticated, sync undecided, never saved',
    { planner: savedPlanner({ savedAt: null }), isAuthenticated: true, syncEnabled: null },
  ],
  [
    'published and current',
    {
      planner: savedPlanner({ published: true, contentVersion: 7 }),
      isAuthenticated: true,
      syncEnabled: true,
    },
  ],
  [
    'published with unpublished changes',
    {
      planner: savedPlanner({ published: true, status: 'draft' }),
      isAuthenticated: true,
      syncEnabled: true,
    },
  ],
  [
    'keywords and an unknown category',
    {
      planner: savedPlanner({}, emptyContent(['Burn', 'Rupture', 'Sinking']), 'UNKNOWN'),
      isAuthenticated: true,
      syncEnabled: true,
    },
  ],
  ['untitled', { planner: savedPlanner({ title: '' }), isAuthenticated: false }],
]

beforeEach(() => {
  vi.clearAllMocks()
  mockRole.current = 'NORMAL'
})

describe('PublishedPlannerHeader DOM', () => {
  it.each(PUBLISHED_MATRIX)('renders %s', (_label, row) => {
    mockRole.current = row.role ?? 'NORMAL'

    const next = render(
      <PublishedPlannerHeader
        planner={row.planner}
        isOwner={row.isOwner}
        isAuthenticated={row.isAuthenticated}
        syncEnabled={row.syncEnabled}
        savedPlannerData={row.savedPlannerData}
        onEdit={row.onEdit}
        onCommentClick={row.onCommentClick}
      />,
      { wrapper },
    )

    expect(next.container.innerHTML).toMatchSnapshot()
  })
})

describe('PersonalPlannerHeader DOM', () => {
  it.each(PERSONAL_MATRIX)('renders %s', (_label, row) => {
    const next = render(
      <PersonalPlannerHeader
        planner={row.planner}
        isAuthenticated={row.isAuthenticated}
        syncEnabled={row.syncEnabled}
        onEdit={row.onEdit}
      />,
      { wrapper },
    )

    expect(next.container.innerHTML).toMatchSnapshot()
  })
})
