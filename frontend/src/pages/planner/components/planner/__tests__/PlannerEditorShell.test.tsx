/**
 * PlannerEditorShell - unload warning.
 *
 * The shell's editing surface is stubbed out: only the beforeunload registration
 * is under test, over the real store and the real usePlannerSave (its two storage
 * adapters faked, as in usePlannerSave.test.ts).
 */

import { describe, it, expect, vi, beforeEach, assert } from 'vitest'
import { render, act, waitFor, screen, fireEvent } from '@testing-library/react'
import type { StoreApi } from 'zustand'
import { ok } from '@/lib/result'
import type { Result } from '@/lib/result'
import type { SaveablePlanner } from '../../../types/PlannerTypes'
import type { AppError } from '@/lib/apiErrorClassifier'
import type { StorageReadError } from '@/lib/storage'

const mockSaveToLocal = vi.fn<(planner: SaveablePlanner) => Promise<Result<void, AppError>>>()
const mockGetOrCreateDeviceId = vi.fn<() => Promise<Result<string, StorageReadError>>>()
const mockSyncToServer = vi.fn<(planner: SaveablePlanner, force?: boolean) => Promise<unknown>>()

// Read at render time, so a test can put the shell on the far side of the two
// conditions a server round-trip needs.
let mockAuthUser: { id: string } | null = null
let mockSyncEnabled = false

vi.mock('@/pages/planner/hooks/usePlannerStorage', () => ({
  usePlannerStorage: () => ({
    getOrCreateDeviceId: mockGetOrCreateDeviceId,
    saveToLocal: mockSaveToLocal,
    deleteFromLocal: vi.fn(),
    loadFromLocal: vi.fn(),
    listLocal: vi.fn(),
    listLocalFull: vi.fn(),
  }),
}))

vi.mock('@/pages/planner/hooks/usePlannerSyncAdapter', () => ({
  usePlannerSyncAdapter: () => ({
    syncToServer: mockSyncToServer,
    fetchFromServer: vi.fn(),
    deleteFromServer: vi.fn(),
    listFromServer: vi.fn(),
  }),
}))

vi.mock('@/shared/auth', () => ({
  useAuthQuery: () => ({ data: mockAuthUser }),
  authQueryKeys: { me: ['auth', 'me'] as const },
}))

vi.mock('@/pages/egoGift', () => ({
  useEGOGiftListData: () => ({ spec: {}, i18n: {} }),
}))

vi.mock('@/shared/userSettings', () => ({
  useUserSettingsQuery: () => ({ data: { syncEnabled: mockSyncEnabled } }),
}))

// The presenter's sink, and translation as the identity so the key it picks is
// what the assertion reads.
vi.mock('sonner', () => {
  const toastFn = Object.assign(vi.fn(), {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
    dismiss: vi.fn(),
  })
  return { toast: toastFn }
})

vi.mock('@/lib/i18n', () => ({ default: { t: (key: string) => key } }))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

vi.mock('../../../hooks/useDeckClipboard', () => ({
  useDeckClipboard: () => ({
    handleImport: vi.fn(),
    handleExport: vi.fn(),
    pendingImport: null,
    clearPending: vi.fn(),
  }),
}))

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      // The second argument is a fallback string at some call sites and
      // interpolation options at others; only the former may be returned.
      t: (key: string, fallback?: unknown) => (typeof fallback === 'string' ? fallback : key),
      i18n: { language: 'EN' },
    }),
  }
})

// The editing surface itself is irrelevant here, and mounting it would pull in
// every game-data query plus a Tiptap editor per section.
vi.mock('../../deckBuilder/DeckBuilderSummary', () => ({
  StoreBoundDeckBuilderSummary: () => null,
}))
vi.mock('../../deckBuilder/DeckBuilderPane', () => ({ DeckBuilderPane: () => null }))
vi.mock('../../deckBuilder/DeckBuilderContent', () => ({
  StoreBoundDeckBuilderContent: () => null,
}))
vi.mock('../../deckBuilder/DeckImportConfirmDialog', () => ({
  DeckImportConfirmDialog: () => null,
}))
vi.mock('../../startBuff/StartBuffSection', () => ({ StoreBoundStartBuffSection: () => null }))
vi.mock('../../startBuff/StartBuffEditPane', () => ({ StartBuffEditPane: () => null }))
vi.mock('../../startGift/StartGiftSummary', () => ({ StoreBoundStartGiftSummary: () => null }))
vi.mock('../../startGift/StartGiftEditPane', () => ({ StartGiftEditPane: () => null }))
vi.mock('../../egoGift/EGOGiftObservationSummary', () => ({
  StoreBoundEGOGiftObservationSummary: () => null,
}))
vi.mock('../../egoGift/EGOGiftObservationEditPane', () => ({
  EGOGiftObservationEditPane: () => null,
}))
vi.mock('../../egoGift/ComprehensiveGiftSummary', () => ({
  StoreBoundComprehensiveGiftSummary: () => null,
}))
vi.mock('../../egoGift/ComprehensiveGiftSelectorPane', () => ({
  ComprehensiveGiftSelectorPane: () => null,
}))
vi.mock('../../skillReplacement/SkillReplacementSection', () => ({
  StoreBoundSkillReplacementSection: () => null,
}))
vi.mock('../../floorTheme/FloorThemeGiftSection', () => ({ FloorThemeGiftSection: () => null }))
vi.mock('../StoreBoundSectionNote', () => ({ StoreBoundSectionNote: () => null }))
// Rendered as a marker rather than nothing: whether it is open is the subject
// of the conflict tests below.
vi.mock('../ConflictResolutionDialog', () => ({
  ConflictResolutionDialog: ({ open }: { open: boolean }) =>
    open ? <div data-testid="conflict-dialog" /> : null,
}))
vi.mock('../../SyncOffWarningDialog', () => ({ SyncOffWarningDialog: () => null }))
vi.mock('../KeywordSelector', () => ({ KeywordSelector: () => null }))

import { PlannerEditorShell } from '../PlannerEditorShell'
import { toast as sonnerToast } from 'sonner'
import { ConflictError } from '@/lib/apiErrors'
import {
  PlannerEditorStoreProvider,
  usePlannerEditorStoreApi,
} from '../../../stores/usePlannerEditorStore'
import type { PlannerEditorStore } from '../../../stores/usePlannerEditorStore'

function StoreCapture({ onReady }: { onReady: (api: StoreApi<PlannerEditorStore>) => void }) {
  onReady(usePlannerEditorStoreApi())
  return null
}

/** Fire the event the browser fires on tab close, and report whether it was vetoed. */
function fireBeforeUnload(): boolean {
  const event = new Event('beforeunload', { cancelable: true })
  window.dispatchEvent(event)
  return event.defaultPrevented
}

function renderShell() {
  let storeApi: StoreApi<PlannerEditorStore> | null = null
  const utils = render(
    <PlannerEditorStoreProvider>
      <StoreCapture
        onReady={(api) => {
          storeApi = api
        }}
      />
      <PlannerEditorShell contentVersion={7} />
    </PlannerEditorStoreProvider>,
  )
  if (!storeApi) throw new Error('store api was not captured')
  return { ...utils, storeApi: storeApi as StoreApi<PlannerEditorStore> }
}

beforeEach(() => {
  vi.clearAllMocks()
  mockGetOrCreateDeviceId.mockResolvedValue(ok('device-123'))
  mockSaveToLocal.mockResolvedValue(ok(undefined))
  mockAuthUser = null
  mockSyncEnabled = false
})

/** Press the header's save button and let the save settle. */
async function clickSave() {
  const saveButton = screen.getAllByRole('button', { name: 'pages.plannerMD.save.button' })[0]
  assert(saveButton, 'the shell renders no save button')
  await act(async () => {
    fireEvent.click(saveButton)
  })
}

describe('PlannerEditorShell - unload warning', () => {
  it('does not warn on a freshly mounted, untouched planner', () => {
    renderShell()

    expect(fireBeforeUnload()).toBe(false)
  })

  it('warns on a store write that has not re-rendered the shell', () => {
    const { storeApi } = renderShell()

    // deploymentOrder is not one of the slices the shell selects, so this write
    // notifies the save subscription without producing a render.
    storeApi.getState().setDeploymentOrder([3, 1, 2])

    expect(fireBeforeUnload()).toBe(true)
  })

  it('stops warning once a manual save has adopted what it wrote', async () => {
    const { storeApi } = renderShell()

    storeApi.getState().setDeploymentOrder([3, 1, 2])
    expect(fireBeforeUnload()).toBe(true)

    await clickSave()

    await waitFor(() => {
      expect(mockSaveToLocal).toHaveBeenCalled()
    })
    expect(fireBeforeUnload()).toBe(false)
  })

  it('stops warning once the pending autosave has written', async () => {
    const { storeApi } = renderShell()

    // The first autosave only adopts a baseline; the second write is the one that
    // reaches storage.
    act(() => {
      storeApi.getState().setDeploymentOrder([3, 1, 2])
    })
    await waitFor(() => {
      expect(fireBeforeUnload()).toBe(false)
    })

    act(() => {
      storeApi.getState().setDeploymentOrder([1, 2, 3])
    })
    expect(fireBeforeUnload()).toBe(true)

    await waitFor(() => {
      expect(mockSaveToLocal).toHaveBeenCalledTimes(1)
    })
    await waitFor(() => {
      expect(fireBeforeUnload()).toBe(false)
    })
  })
})

/**
 * A rejected server write, over a signed-in account with sync on — the only
 * route by which the shell holds a conflict at all.
 */
describe('PlannerEditorShell - a rejected server write', () => {
  beforeEach(() => {
    mockAuthUser = { id: 'user-1' }
    mockSyncEnabled = true
  })

  it('reports a conflict the dialog cannot resolve instead of opening it', async () => {
    mockSyncToServer.mockRejectedValue(
      new ConflictError('PLANNER_LIMIT_EXCEEDED', 'too many planners', null),
    )
    const { storeApi } = renderShell()
    storeApi.getState().setDeploymentOrder([3, 1, 2])

    await clickSave()

    await waitFor(() => {
      expect(sonnerToast.error).toHaveBeenCalledWith('common:errors.generic.message', {
        description: expect.anything(),
      })
    })
    expect(screen.queryByTestId('conflict-dialog')).toBeNull()
  })

  it('opens the dialog for the conflict that carries a server version, and says nothing', async () => {
    mockSyncToServer.mockRejectedValue(new ConflictError('SYNC_CONFLICT', 'conflict', 9))
    const { storeApi } = renderShell()
    storeApi.getState().setDeploymentOrder([3, 1, 2])

    await clickSave()

    await waitFor(() => {
      expect(screen.getByTestId('conflict-dialog')).toBeInTheDocument()
    })
    expect(sonnerToast.error).not.toHaveBeenCalled()
    expect(sonnerToast.warning).not.toHaveBeenCalled()
  })
})
